package com.moviebooking.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Row lock so concurrent pay/cancel calls on the same booking are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from Booking b join fetch b.show s join fetch s.movie where b.id = :id")
    Optional<Booking> findByIdWithShow(@Param("id") Long id);

    Optional<Booking> findByHoldId(String holdId);

    @Query("select b from Booking b join fetch b.show s join fetch s.movie where b.user.id = :userId order by b.createdAt desc")
    List<Booking> findByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update Booking b set b.status = :expired, b.updatedAt = :now " +
            "where b.status = :pending and b.holdExpiresAt < :now")
    int expirePending(@Param("now") Instant now,
                      @Param("pending") BookingStatus pending, @Param("expired") BookingStatus expired);

    @Query("select b from Booking b join fetch b.show s join fetch s.movie " +
            "where b.status = :status and s.startTime between :from and :to")
    List<Booking> findByStatusAndShowStartBetween(@Param("status") BookingStatus status,
                                                  @Param("from") Instant from, @Param("to") Instant to);
}
