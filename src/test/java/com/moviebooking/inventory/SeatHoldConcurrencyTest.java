package com.moviebooking.inventory;

import com.moviebooking.TestCatalog;
import com.moviebooking.booking.BookingDtos.CreateBookingRequest;
import com.moviebooking.booking.BookingDtos.PayRequest;
import com.moviebooking.booking.BookingService;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatRepository;
import com.moviebooking.common.ApiException;
import com.moviebooking.inventory.InventoryDtos.HoldResponse;
import com.moviebooking.inventory.InventoryDtos.SeatAvailability;
import com.moviebooking.user.Role;
import com.moviebooking.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The core invariant: for a given show and seat, at most one active allocation exists, no matter how many
 * requests race. These tests run real transactions against the database on real threads.
 */
@SpringBootTest
class SeatHoldConcurrencyTest {

    @Autowired SeatHoldService holds;
    @Autowired SeatAllocationRepository allocations;
    @Autowired SeatRepository seats;
    @Autowired BookingService bookings;
    @Autowired TestCatalog catalog;

    private TestCatalog.Fixture fx;
    private List<Seat> screenSeats;
    private List<User> users;

    @BeforeEach
    void setUp() {
        fx = catalog.createShow();
        screenSeats = seats.findByScreenIdOrderByRowLabelAscSeatNumberAsc(fx.screenId());
        users = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            users.add(catalog.user("cust" + i + "-" + UUID.randomUUID().toString().substring(0, 6) + "@t.io", Role.CUSTOMER));
        }
    }

    @Test
    void twentyCustomersRaceForTheSameSeat_exactlyOneWins() throws Exception {
        Long seatId = screenSeats.get(0).getId();
        int outcomeSuccess = race(users.size(), i -> holds.hold(fx.showId(), List.of(seatId), users.get(i).getId()));

        assertThat(outcomeSuccess).isEqualTo(1);
        assertThat(allocations.findByShowId(fx.showId())).hasSize(1);
    }

    @Test
    void overlappingSeatSets_neverDoubleAllocateAndNeverPartiallyHold() throws Exception {
        Long s1 = screenSeats.get(0).getId();
        Long s2 = screenSeats.get(1).getId();
        Long s3 = screenSeats.get(2).getId();
        // even threads want {s1,s2}, odd threads want {s2,s3}; s2 is the contended seat
        int successes = race(10, i -> holds.hold(fx.showId(),
                i % 2 == 0 ? List.of(s1, s2) : List.of(s2, s3), users.get(i).getId()));

        assertThat(successes).isEqualTo(1);
        List<SeatAllocation> rows = allocations.findByShowId(fx.showId());
        assertThat(rows).hasSize(2); // one winner, both of its seats, nothing else
        assertThat(rows).extracting(SeatAllocation::getHoldId).containsOnly(rows.get(0).getHoldId());
    }

    @Test
    void secondHoldFailsWhileFirstIsActive_thenSucceedsAfterExpiry() {
        Long seatId = screenSeats.get(3).getId();
        HoldResponse first = holds.hold(fx.showId(), List.of(seatId), users.get(0).getId());

        assertThatThrownBy(() -> holds.hold(fx.showId(), List.of(seatId), users.get(1).getId()))
                .isInstanceOf(ApiException.SeatUnavailable.class);
        assertThat(holds.seatMap(fx.showId()).seats().stream().filter(s -> s.seatId().equals(seatId)).findFirst()
                .orElseThrow().availability()).isEqualTo(SeatAvailability.HELD);

        expire(first.holdId());

        // expired hold shows as AVAILABLE and can be taken by someone else
        assertThat(holds.seatMap(fx.showId()).seats().stream().filter(s -> s.seatId().equals(seatId)).findFirst()
                .orElseThrow().availability()).isEqualTo(SeatAvailability.AVAILABLE);
        HoldResponse second = holds.hold(fx.showId(), List.of(seatId), users.get(1).getId());
        assertThat(second.holdId()).isNotEqualTo(first.holdId());
    }

    @Test
    void payingWithAnExpiredHoldFailsAndExpiresTheBooking() {
        Long seatId = screenSeats.get(4).getId();
        Long userId = users.get(0).getId();
        HoldResponse hold = holds.hold(fx.showId(), List.of(seatId), userId);
        var booking = bookings.create(new CreateBookingRequest(hold.holdId(), null), userId);

        expire(hold.holdId());

        assertThatThrownBy(() -> bookings.pay(booking.id(), new PayRequest(UUID.randomUUID().toString(), "CARD"), userId))
                .isInstanceOf(ApiException.HoldExpired.class);
        assertThat(bookings.get(booking.id(), userId).status()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void releaseIsIdempotentAndOwnerScoped() {
        Long seatId = screenSeats.get(5).getId();
        HoldResponse hold = holds.hold(fx.showId(), List.of(seatId), users.get(0).getId());

        holds.release(hold.holdId(), users.get(1).getId()); // not the owner: no effect
        assertThat(allocations.countByHoldId(hold.holdId())).isEqualTo(1);

        holds.release(hold.holdId(), users.get(0).getId());
        holds.release(hold.holdId(), users.get(0).getId()); // second call is a no-op
        assertThat(allocations.countByHoldId(hold.holdId())).isZero();
    }

    // ---- helpers -------------------------------------------------------------

    private void expire(String holdId) {
        List<SeatAllocation> rows = allocations.findByHoldId(holdId);
        rows.forEach(r -> r.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1))));
        allocations.saveAll(rows);
    }

    private interface Attempt {
        void run(int index);
    }

    /** Starts N threads on a latch so they hit the database at the same moment; returns the number that succeeded. */
    private static int race(int threads, Attempt attempt) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    attempt.run(idx);
                    successes.incrementAndGet();
                } catch (ApiException.SeatUnavailable expected) {
                    // the losing side of the race
                } catch (Throwable t) {
                    unexpected.incrementAndGet();
                    t.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(unexpected.get()).as("unexpected exceptions").isZero();
        return successes.get();
    }
}
