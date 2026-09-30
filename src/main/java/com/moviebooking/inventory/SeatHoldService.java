package com.moviebooking.inventory;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatRepository;
import com.moviebooking.catalog.Show;
import com.moviebooking.catalog.ShowRepository;
import com.moviebooking.common.ApiException;
import com.moviebooking.config.BookingProperties;
import com.moviebooking.inventory.InventoryDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Seat availability and temporary holds.
 * <p>
 * Concurrency model, in one paragraph: a hold is an INSERT into seat_allocations for each requested seat, all in
 * one transaction. The unique (show_id, seat_id) constraint is the lock. If two users race for A10, both
 * transactions try to insert the same key; the database lets exactly one commit and the other fails with a
 * constraint violation, which we translate into a 409 SEAT_UNAVAILABLE. Because all seats of a request are in one
 * transaction, a multi-seat hold is all-or-nothing. Expired holds are simply deleted (lazily at hold time, and by a
 * scheduled sweep), so a stale hold can never block a seat for longer than the hold duration.
 */
@Service
public class SeatHoldService {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);

    private final SeatAllocationRepository allocations;
    private final ShowRepository shows;
    private final SeatRepository seats;
    private final BookingProperties props;
    private final TransactionTemplate tx;

    public SeatHoldService(SeatAllocationRepository allocations, ShowRepository shows, SeatRepository seats,
                           BookingProperties props, PlatformTransactionManager txManager) {
        this.allocations = allocations;
        this.shows = shows;
        this.seats = seats;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- Customer operations ------------------------------------------------

    public HoldResponse hold(Long showId, List<Long> requestedSeatIds, Long userId) {
        List<Long> seatIds = requestedSeatIds.stream().distinct().toList();
        try {
            // The transaction is committed inside execute(); a unique-constraint race surfaces here as
            // DataIntegrityViolationException *after* rollback, which is why the catch sits outside the tx.
            return tx.execute(status -> doHold(showId, seatIds, userId));
        } catch (DataIntegrityViolationException e) {
            log.info("Hold lost race for show {} seats {}", showId, seatIds);
            throw new ApiException.SeatUnavailable("One or more seats were just taken by another customer");
        }
    }

    private HoldResponse doHold(Long showId, List<Long> seatIds, Long userId) {
        Instant now = Instant.now();
        Show show = shows.findById(showId).orElseThrow(() -> new ApiException.NotFound("Show", showId));
        if (!show.getStartTime().isAfter(now)) {
            throw new ApiException.InvalidState("Show has already started");
        }

        List<Seat> seatList = seats.findByIdInAndScreenId(seatIds, show.getScreen().getId());
        if (seatList.size() != seatIds.size()) {
            throw new ApiException.BadRequest("One or more seat ids do not belong to this show's screen");
        }

        // 1. Clear stale holds for these seats so an abandoned hold never blocks a fresh one.
        allocations.deleteExpiredForSeats(showId, seatIds, now, AllocationStatus.HELD);

        // 2. Friendly pre-check (not the guarantee; the unique constraint is).
        List<SeatAllocation> taken = allocations.findByShowIdAndSeatIdIn(showId, seatIds);
        if (!taken.isEmpty()) {
            String labels = taken.stream().map(a -> a.getSeat().label()).sorted().collect(Collectors.joining(", "));
            throw new ApiException.SeatUnavailable("Seats not available: " + labels);
        }

        // 3. Insert. On a race, flush throws DataIntegrityViolationException and the whole hold rolls back.
        String holdId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(Duration.ofMinutes(props.holdDurationMinutes()));
        List<SeatAllocation> rows = seatList.stream()
                .map(seat -> new SeatAllocation(show, seat, holdId, userId, expiresAt))
                .toList();
        allocations.saveAllAndFlush(rows);

        List<String> labels = seatList.stream().map(Seat::label).sorted().toList();
        return new HoldResponse(holdId, showId, labels, expiresAt);
    }

    /** Idempotent: releasing an unknown or already-released hold is a no-op. */
    @Transactional
    public void release(String holdId, Long userId) {
        allocations.deleteHold(holdId, userId, AllocationStatus.HELD);
    }

    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long showId) {
        Show show = shows.findById(showId).orElseThrow(() -> new ApiException.NotFound("Show", showId));
        Instant now = Instant.now();

        Map<Long, SeatAllocation> bySeat = new HashMap<>();
        for (SeatAllocation a : allocations.findByShowId(showId)) {
            bySeat.put(a.getSeat().getId(), a);
        }

        List<SeatView> views = new ArrayList<>();
        int available = 0;
        for (Seat seat : seats.findByScreenIdOrderByRowLabelAscSeatNumberAsc(show.getScreen().getId())) {
            SeatAvailability availability = availabilityOf(bySeat.get(seat.getId()), now);
            if (availability == SeatAvailability.AVAILABLE) available++;
            views.add(new SeatView(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(), seat.label(),
                    seat.getTier(), show.priceFor(seat.getTier()), availability));
        }
        return new SeatMapResponse(showId, views.size(), available, views);
    }

    private static SeatAvailability availabilityOf(SeatAllocation a, Instant now) {
        if (a == null) return SeatAvailability.AVAILABLE;
        if (a.getStatus() == AllocationStatus.BOOKED) return SeatAvailability.BOOKED;
        return a.isActiveHold(now) ? SeatAvailability.HELD : SeatAvailability.AVAILABLE;
    }

    // ---- Used by the booking module (must be called inside the caller's transaction) --------------------

    /** Returns the hold's rows locked FOR UPDATE, or throws if the hold is gone/expired/not the caller's. */
    public List<SeatAllocation> requireActiveHoldForUpdate(String holdId, Long userId) {
        List<SeatAllocation> rows = allocations.findByHoldIdForUpdate(holdId);
        Instant now = Instant.now();
        if (rows.isEmpty()) {
            throw new ApiException.HoldExpired("Hold not found or already released: " + holdId);
        }
        if (!rows.get(0).getUserId().equals(userId)) {
            throw new ApiException.Forbidden("This hold belongs to another customer");
        }
        if (rows.stream().anyMatch(r -> !r.isActiveHold(now))) {
            throw new ApiException.HoldExpired("Hold has expired or was already converted: " + holdId);
        }
        return rows;
    }

    public List<SeatAllocation> findHold(String holdId) {
        return allocations.findByHoldId(holdId);
    }

    public void convertHoldToBooking(String holdId, Long bookingId) {
        allocations.markBooked(holdId, bookingId, AllocationStatus.HELD, AllocationStatus.BOOKED);
    }

    public void releaseHoldUnchecked(String holdId, Long userId) {
        allocations.deleteHold(holdId, userId, AllocationStatus.HELD);
    }

    public void releaseBookingSeats(Long bookingId) {
        allocations.deleteByBookingId(bookingId);
    }

    // ---- Maintenance ---------------------------------------------------------

    @Transactional
    public int expireHolds() {
        return allocations.deleteExpired(Instant.now(), AllocationStatus.HELD);
    }
}
