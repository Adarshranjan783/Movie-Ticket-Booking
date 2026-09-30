package com.moviebooking.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refund_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Refund {

    public enum Status {
        SUCCESS, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Percentage of the paid amount that was refunded, for transparency. */
    @Column(nullable = false)
    private int refundPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "provider_ref", length = 80)
    private String providerRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Refund(Booking booking, BigDecimal amount, int refundPercent, Status status, String providerRef) {
        this.booking = booking;
        this.amount = amount;
        this.refundPercent = refundPercent;
        this.status = status;
        this.providerRef = providerRef;
    }
}
