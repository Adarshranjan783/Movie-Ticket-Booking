package com.moviebooking.inventory;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.Show;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The single source of truth for "is this seat taken for this show?".
 * <p>
 * Correctness guarantee: the unique constraint on (show_id, seat_id) means the database itself rejects a
 * second allocation for the same seat, no matter how many application threads or instances race.
 * A seat is AVAILABLE when no row exists (or the only row is an expired HELD one).
 */
@Entity
@Table(name = "seat_allocations",
        uniqueConstraints = @UniqueConstraint(name = "uk_allocation_show_seat", columnNames = {"show_id", "seat_id"}),
        indexes = {
                @Index(name = "idx_allocation_hold", columnList = "hold_id"),
                @Index(name = "idx_allocation_booking", columnList = "booking_id"),
                @Index(name = "idx_allocation_expiry", columnList = "status, expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class SeatAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AllocationStatus status;

    /** Groups the seats of one hold request; carried onto the booking. */
    @Column(name = "hold_id", nullable = false, length = 36)
    private String holdId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Null once BOOKED. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Set when the hold is converted into a confirmed booking. */
    @Column(name = "booking_id")
    private Long bookingId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SeatAllocation(Show show, Seat seat, String holdId, Long userId, Instant expiresAt) {
        this.show = show;
        this.seat = seat;
        this.status = AllocationStatus.HELD;
        this.holdId = holdId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isActiveHold(Instant now) {
        return status == AllocationStatus.HELD && expiresAt != null && expiresAt.isAfter(now);
    }
}
