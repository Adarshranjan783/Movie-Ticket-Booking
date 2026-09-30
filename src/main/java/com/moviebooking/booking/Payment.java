package com.moviebooking.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per payment attempt. The unique idempotency key is what makes a retried request harmless:
 * the second attempt finds the first row and returns its outcome instead of charging again.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_idempotency", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_payment_booking", columnList = "booking_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    public enum Status {
        SUCCESS, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "provider_ref", length = 80)
    private String providerRef;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Payment(Booking booking, String idempotencyKey, BigDecimal amount, Status status,
                   String providerRef, String failureReason) {
        this.booking = booking;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = status;
        this.providerRef = providerRef;
        this.failureReason = failureReason;
    }
}
