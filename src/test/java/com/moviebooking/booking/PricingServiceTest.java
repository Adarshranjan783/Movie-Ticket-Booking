package com.moviebooking.booking;

import com.moviebooking.catalog.*;
import com.moviebooking.config.BookingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final PricingService pricing = new PricingService(new BookingProperties(5, 20, 24, "Asia/Kolkata", false));

    private static Show show(Instant start) {
        Screen screen = new Screen(new Theater(new City("X"), "T", null), "S");
        return new Show(new Movie("M", 120, null, null), screen, start, start.plus(Duration.ofHours(2)),
                new BigDecimal("200.00"), new BigDecimal("350.00"), null);
    }

    private static Seat seat(long id, String row, int number, SeatTier tier) {
        Seat s = new Seat(null, row, number, tier);
        s.setId(id);
        return s;
    }

    private static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(IST).toInstant();
    }

    @Test
    void weekdayPricingUsesTierPricesWithoutSurcharge() {
        Show show = show(at("2026-10-07T18:00:00")); // Wednesday
        List<Seat> seats = List.of(seat(1, "A", 1, SeatTier.REGULAR), seat(2, "C", 1, SeatTier.PREMIUM));

        PricingService.Quote q = pricing.quote(show, seats, null);

        assertThat(q.weekend()).isFalse();
        assertThat(q.subtotal()).isEqualByComparingTo("550.00");
        assertThat(q.discount()).isEqualByComparingTo("0.00");
        assertThat(q.total()).isEqualByComparingTo("550.00");
        assertThat(q.lines()).extracting(PricingService.PriceLine::weekendSurcharge)
                .allSatisfy(s -> assertThat(s).isEqualByComparingTo("0.00"));
    }

    @Test
    void weekendAddsConfiguredSurchargePerSeat() {
        Show show = show(at("2026-10-10T18:00:00")); // Saturday
        List<Seat> seats = List.of(seat(1, "A", 1, SeatTier.REGULAR), seat(2, "C", 1, SeatTier.PREMIUM));

        PricingService.Quote q = pricing.quote(show, seats, null);

        assertThat(q.weekend()).isTrue();
        // 200 + 20% = 240, 350 + 20% = 420
        assertThat(q.lines().get(0).price()).isEqualByComparingTo("240.00");
        assertThat(q.lines().get(1).price()).isEqualByComparingTo("420.00");
        assertThat(q.total()).isEqualByComparingTo("660.00");
    }

    @Test
    void percentCouponIsCappedByMaxDiscount() {
        Show show = show(at("2026-10-07T18:00:00"));
        List<Seat> seats = List.of(seat(1, "C", 1, SeatTier.PREMIUM), seat(2, "C", 2, SeatTier.PREMIUM)); // 700
        Coupon coupon = new Coupon("SAVE50", Coupon.DiscountType.PERCENT, new BigDecimal("50"),
                new BigDecimal("100.00"), Instant.EPOCH, Instant.MAX);

        PricingService.Quote q = pricing.quote(show, seats, coupon);

        assertThat(q.discount()).isEqualByComparingTo("100.00"); // 50% of 700 = 350, capped at 100
        assertThat(q.total()).isEqualByComparingTo("600.00");
        assertThat(q.couponCode()).isEqualTo("SAVE50");
    }

    @Test
    void flatCouponNeverExceedsSubtotal() {
        Show show = show(at("2026-10-07T18:00:00"));
        List<Seat> seats = List.of(seat(1, "A", 1, SeatTier.REGULAR)); // 200
        Coupon coupon = new Coupon("BIG", Coupon.DiscountType.FLAT, new BigDecimal("500"), null, Instant.EPOCH, Instant.MAX);

        PricingService.Quote q = pricing.quote(show, seats, coupon);

        assertThat(q.discount()).isEqualByComparingTo("200.00");
        assertThat(q.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void couponValidityWindowIsRespected() {
        Instant now = Instant.now();
        Coupon expired = new Coupon("OLD", Coupon.DiscountType.FLAT, BigDecimal.TEN, null,
                now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        Coupon live = new Coupon("NEW", Coupon.DiscountType.FLAT, BigDecimal.TEN, null,
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));

        assertThat(expired.isUsableAt(now)).isFalse();
        assertThat(live.isUsableAt(now)).isTrue();
        live.setActive(false);
        assertThat(live.isUsableAt(now)).isFalse();
    }
}
