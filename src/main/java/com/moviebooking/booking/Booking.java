package com.moviebooking.booking;

import com.moviebooking.catalog.Show;
import com.moviebooking.common.ApiException;
import com.moviebooking.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_ref", columnNames = "booking_ref"),
                // one hold can only ever become one booking
                @UniqueConstraint(name = "uk_booking_hold", columnNames = "hold_id")
        },
        indexes = {
                @Index(name = "idx_booking_user", columnList = "user_id, created_at"),
                @Index(name = "idx_booking_show_status", columnList = "show_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Customer-facing reference, safe to print on a ticket. */
    @Column(name = "booking_ref", nullable = false, length = 36)
    private String bookingRef = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(name = "hold_id", nullable = false, length = 36)
    private String holdId;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> seats = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Booking(User user, Show show, String holdId, Instant holdExpiresAt,
                   BigDecimal subtotal, BigDecimal discount, BigDecimal total, String couponCode) {
        this.user = user;
        this.show = show;
        this.holdId = holdId;
        this.holdExpiresAt = holdExpiresAt;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.couponCode = couponCode;
    }

    public void addSeat(BookingSeat seat) {
        seat.setBooking(this);
        seats.add(seat);
    }

    /** The only way to change status; rejects invalid transitions instead of silently overwriting. */
    public void transitionTo(BookingStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException.InvalidState("Booking " + bookingRef + " cannot move from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
