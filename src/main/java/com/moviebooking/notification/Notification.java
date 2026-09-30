package com.moviebooking.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Outbox-style record of every notification. "Delivery" in this project means writing it here and logging it;
 * swapping in email/SMS/push only touches NotificationService.deliver().
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id, created_at"),
        @Index(name = "idx_notification_booking_type", columnList = "booking_id, type")
})
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public enum Type {
        BOOKING_CONFIRMED, BOOKING_CANCELLED, REFUND_PROCESSED, SHOW_REMINDER
    }

    public enum Status {
        PENDING, SENT, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    public Notification(Long userId, Long bookingId, Type type, String message) {
        this.userId = userId;
        this.bookingId = bookingId;
        this.type = type;
        this.message = message;
    }
}
