package com.moviebooking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.Payment;
import com.moviebooking.booking.PaymentRepository;
import com.moviebooking.user.Role;
import com.moviebooking.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks the whole product through the HTTP API:
 * admin sets up the catalog -> customer discovers -> holds -> books with a coupon -> pays (with a failure,
 * a success and an idempotent retry) -> cancels -> refund + seats released -> notifications recorded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestCatalog catalog;
    @Autowired PaymentRepository payments;

    private RequestPostProcessor admin;
    private RequestPostProcessor alice;
    private RequestPostProcessor bob;

    @BeforeEach
    void users() {
        String tag = UUID.randomUUID().toString().substring(0, 6);
        User a = catalog.user("admin-" + tag + "@t.io", Role.ADMIN);
        User al = catalog.user("alice-" + tag + "@t.io", Role.CUSTOMER);
        User b = catalog.user("bob-" + tag + "@t.io", Role.CUSTOMER);
        admin = SecurityMockMvcRequestPostProcessors.httpBasic(a.getEmail(), "secret123");
        alice = SecurityMockMvcRequestPostProcessors.httpBasic(al.getEmail(), "secret123");
        bob = SecurityMockMvcRequestPostProcessors.httpBasic(b.getEmail(), "secret123");
    }

    @Test
    void endToEndBookingLifecycle() throws Exception {
        // ---- Admin sets up the catalog ---------------------------------------------------------
        String tag = UUID.randomUUID().toString().substring(0, 6);
        long policyId = post("/api/admin/refund-policies", admin, """
                {"name":"Std-%s","fullRefundHoursBefore":24,"partialRefundHoursBefore":4,"partialRefundPercent":50,"defaultPolicy":true}
                """.formatted(tag), 201).get("id").asLong();
        long cityId = post("/api/admin/cities", admin, """
                {"name":"City-%s"}""".formatted(tag), 201).get("id").asLong();
        long theaterId = post("/api/admin/theaters", admin, """
                {"cityId":%d,"name":"Theater-%s","address":"Main St"}""".formatted(cityId, tag), 201).get("id").asLong();
        JsonNode screen = post("/api/admin/screens", admin, """
                {"theaterId":%d,"name":"Audi 1","rows":3,"seatsPerRow":4,"premiumRows":["C"]}""".formatted(theaterId), 201);
        long screenId = screen.get("id").asLong();
        assertThat(screen.get("seatCount").asInt()).isEqualTo(12);
        long movieId = post("/api/admin/movies", admin, """
                {"title":"Movie-%s","durationMinutes":120,"language":"English","genre":"Drama"}""".formatted(tag), 201).get("id").asLong();
        Instant start = catalog.weekdayStart(java.time.Duration.ofDays(2));
        long showId = post("/api/admin/shows", admin, """
                {"movieId":%d,"screenId":%d,"startTime":"%s","regularPrice":200.00,"premiumPrice":350.00,"refundPolicyId":%d}
                """.formatted(movieId, screenId, start, policyId), 201).get("id").asLong();
        post("/api/admin/coupons", admin, """
                {"code":"FLAT50-%s","type":"FLAT","value":50.00,"validFrom":"%s","validTo":"%s"}
                """.formatted(tag, Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400)), 201);

        // overlapping show on the same screen is rejected
        post("/api/admin/shows", admin, """
                {"movieId":%d,"screenId":%d,"startTime":"%s","regularPrice":200.00,"premiumPrice":350.00}
                """.formatted(movieId, screenId, start.plusSeconds(1800)), 409);

        // ---- RBAC ----------------------------------------------------------------------------------
        post("/api/admin/cities", alice, "{\"name\":\"Nope\"}", 403);
        mvc.perform(MockMvcRequestBuilders.post("/api/shows/" + showId + "/holds")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\":[1]}"))
                .andExpect(status().isUnauthorized());

        // ---- Customer discovery (public) --------------------------------------------------------------
        JsonNode shows = get("/api/shows?cityId=" + cityId, null, 200);
        assertThat(shows).hasSize(1);
        assertThat(shows.get(0).get("id").asLong()).isEqualTo(showId);

        JsonNode seatMap = get("/api/shows/" + showId + "/seats", null, 200);
        assertThat(seatMap.get("availableSeats").asInt()).isEqualTo(12);
        long a1 = seatId(seatMap, "A1");
        long a2 = seatId(seatMap, "A2");
        long c1 = seatId(seatMap, "C1");

        // ---- Hold ----------------------------------------------------------------------------------
        JsonNode hold = post("/api/shows/" + showId + "/holds", alice,
                "{\"seatIds\":[%d,%d,%d]}".formatted(a1, a2, c1), 201);
        String holdId = hold.get("holdId").asText();
        assertThat(hold.get("seatLabels")).hasSize(3);

        // Bob cannot take a held seat; the seat map shows it as HELD
        JsonNode conflict = post("/api/shows/" + showId + "/holds", bob, "{\"seatIds\":[%d]}".formatted(a1), 409);
        assertThat(conflict.get("code").asText()).isEqualTo("SEAT_UNAVAILABLE");
        assertThat(availability(get("/api/shows/" + showId + "/seats", null, 200), "A1")).isEqualTo("HELD");

        // ---- Booking with coupon ---------------------------------------------------------------------
        JsonNode booking = post("/api/bookings", alice,
                "{\"holdId\":\"%s\",\"couponCode\":\"flat50-%s\"}".formatted(holdId, tag), 201);
        long bookingId = booking.get("id").asLong();
        assertThat(booking.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(booking.get("subtotal").decimalValue()).isEqualByComparingTo("750.00"); // 200+200+350
        assertThat(booking.get("discount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(booking.get("total").decimalValue()).isEqualByComparingTo("700.00");

        // creating again from the same hold returns the same booking (idempotent)
        assertThat(post("/api/bookings", alice, "{\"holdId\":\"%s\"}".formatted(holdId), 201).get("id").asLong())
                .isEqualTo(bookingId);

        // Bob can't see Alice's booking
        get("/api/bookings/" + bookingId, bob, 404);

        // ---- Payment: failure keeps the booking payable, success confirms, retry is idempotent --------
        JsonNode failed = post("/api/bookings/" + bookingId + "/pay", alice,
                "{\"idempotencyKey\":\"k-fail-%s\",\"paymentMethod\":\"FAIL_CARD\"}".formatted(tag), 200);
        assertThat(failed.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(failed.get("lastPayment").get("status").asText()).isEqualTo("FAILED");

        String key = "k-ok-" + tag;
        JsonNode paid = post("/api/bookings/" + bookingId + "/pay", alice,
                "{\"idempotencyKey\":\"%s\",\"paymentMethod\":\"CARD\"}".formatted(key), 200);
        assertThat(paid.get("status").asText()).isEqualTo("CONFIRMED");

        JsonNode replay = post("/api/bookings/" + bookingId + "/pay", alice,
                "{\"idempotencyKey\":\"%s\",\"paymentMethod\":\"CARD\"}".formatted(key), 200);
        assertThat(replay.get("status").asText()).isEqualTo("CONFIRMED");
        List<Payment> attempts = payments.findByBookingIdOrderByCreatedAtDesc(bookingId);
        assertThat(attempts).filteredOn(p -> p.getStatus() == Payment.Status.SUCCESS).hasSize(1);

        assertThat(availability(get("/api/shows/" + showId + "/seats", null, 200), "A1")).isEqualTo("BOOKED");
        assertThat(get("/api/bookings", alice, 200)).hasSize(1);

        // ---- Cancellation + refund ------------------------------------------------------------------
        JsonNode cancelled = post("/api/bookings/" + bookingId + "/cancel", alice, "", 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("refund").get("percent").asInt()).isEqualTo(100); // > 24h before show
        assertThat(cancelled.get("refund").get("amount").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(cancelled.get("refund").get("status").asText()).isEqualTo("SUCCESS");

        // idempotent second cancel
        assertThat(post("/api/bookings/" + bookingId + "/cancel", alice, "", 200).get("status").asText()).isEqualTo("CANCELLED");

        // seats are free again and Bob can take one
        JsonNode after = get("/api/shows/" + showId + "/seats", null, 200);
        assertThat(after.get("availableSeats").asInt()).isEqualTo(12);
        post("/api/shows/" + showId + "/holds", bob, "{\"seatIds\":[%d]}".formatted(a1), 201);

        // ---- Async notifications landed without blocking any of the above ----------------------------
        JsonNode notifications = awaitNotifications(alice, 3);
        assertThat(notifications).extracting(n -> n.get("type").asText())
                .contains("BOOKING_CONFIRMED", "BOOKING_CANCELLED", "REFUND_PROCESSED");
    }

    @Test
    void validationErrorsAreStructured() throws Exception {
        JsonNode err = post("/api/admin/cities", admin, "{\"name\":\"\"}", 400);
        assertThat(err.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(err.get("fieldErrors").has("name")).isTrue();

        JsonNode notFound = get("/api/shows/999999", null, 404);
        assertThat(notFound.get("code").asText()).isEqualTo("NOT_FOUND");
    }

    // ---- helpers -------------------------------------------------------------

    private JsonNode post(String url, RequestPostProcessor auth, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) req = req.with(auth);
        return body(mvc.perform(req).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode get(String url, RequestPostProcessor auth, int expected) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.get(url);
        if (auth != null) req = req.with(auth);
        return body(mvc.perform(req).andExpect(status().is(expected)).andReturn());
    }

    private JsonNode body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        return s.isBlank() ? json.nullNode() : json.readTree(s);
    }

    private static long seatId(JsonNode seatMap, String label) {
        for (JsonNode s : seatMap.get("seats")) {
            if (s.get("label").asText().equals(label)) return s.get("seatId").asLong();
        }
        throw new AssertionError("no seat " + label);
    }

    private static String availability(JsonNode seatMap, String label) {
        for (JsonNode s : seatMap.get("seats")) {
            if (s.get("label").asText().equals(label)) return s.get("availability").asText();
        }
        throw new AssertionError("no seat " + label);
    }

    private JsonNode awaitNotifications(RequestPostProcessor auth, int atLeast) throws Exception {
        JsonNode last = null;
        for (int i = 0; i < 50; i++) {
            last = get("/api/notifications", auth, 200);
            if (last.size() >= atLeast) return last;
            Thread.sleep(100);
        }
        return last;
    }
}
