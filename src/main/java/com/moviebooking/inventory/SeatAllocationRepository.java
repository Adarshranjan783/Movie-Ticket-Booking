package com.moviebooking.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, Long> {

    @Query("select a from SeatAllocation a join fetch a.seat where a.show.id = :showId")
    List<SeatAllocation> findByShowId(@Param("showId") Long showId);

    @Query("select a from SeatAllocation a join fetch a.seat where a.show.id = :showId and a.seat.id in :seatIds")
    List<SeatAllocation> findByShowIdAndSeatIdIn(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds);

    /** Locks the hold's rows so payment confirmation and expiry cleanup cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SeatAllocation a where a.holdId = :holdId")
    List<SeatAllocation> findByHoldIdForUpdate(@Param("holdId") String holdId);

    @Query("select a from SeatAllocation a join fetch a.seat where a.holdId = :holdId")
    List<SeatAllocation> findByHoldId(@Param("holdId") String holdId);

    long countByHoldId(String holdId);

    @Modifying
    @Query("delete from SeatAllocation a where a.status = :held and a.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now, @Param("held") AllocationStatus held);

    @Modifying
    @Query("delete from SeatAllocation a where a.status = :held and a.expiresAt < :now " +
            "and a.show.id = :showId and a.seat.id in :seatIds")
    int deleteExpiredForSeats(@Param("showId") Long showId, @Param("seatIds") List<Long> seatIds,
                              @Param("now") Instant now, @Param("held") AllocationStatus held);

    @Modifying
    @Query("delete from SeatAllocation a where a.holdId = :holdId and a.userId = :userId and a.status = :held")
    int deleteHold(@Param("holdId") String holdId, @Param("userId") Long userId, @Param("held") AllocationStatus held);

    @Modifying
    @Query("delete from SeatAllocation a where a.bookingId = :bookingId")
    int deleteByBookingId(@Param("bookingId") Long bookingId);

    @Modifying
    @Query("update SeatAllocation a set a.status = :booked, a.expiresAt = null, a.bookingId = :bookingId " +
            "where a.holdId = :holdId and a.status = :held")
    int markBooked(@Param("holdId") String holdId, @Param("bookingId") Long bookingId,
                   @Param("held") AllocationStatus held, @Param("booked") AllocationStatus booked);
}
