package com.moviebooking.notification;

import com.moviebooking.booking.BookingEvents.BookingCancelled;
import com.moviebooking.booking.BookingEvents.BookingConfirmed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Listens for booking events AFTER the booking transaction commits and handles them on a separate thread,
 * so the booking API never waits for (or fails because of) notification delivery.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmed e) {
        send(new Notification(e.userId(), e.bookingId(), Notification.Type.BOOKING_CONFIRMED,
                "Booking %s confirmed for %s, seats %s. Amount paid: %s"
                        .formatted(e.bookingRef(), e.movieTitle(), e.seatLabels(), e.amountPaid())));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelled e) {
        send(new Notification(e.userId(), e.bookingId(), Notification.Type.BOOKING_CANCELLED,
                "Booking %s for %s has been cancelled.".formatted(e.bookingRef(), e.movieTitle())));
        if (e.refundAmount().signum() > 0) {
            send(new Notification(e.userId(), e.bookingId(), Notification.Type.REFUND_PROCESSED,
                    e.refundSucceeded()
                            ? "Refund of %s for booking %s has been processed.".formatted(e.refundAmount(), e.bookingRef())
                            : "Refund of %s for booking %s could not be processed; we will retry.".formatted(e.refundAmount(), e.bookingRef())));
        }
    }

    @Transactional
    public void send(Notification n) {
        n.setAttempts(n.getAttempts() + 1);
        try {
            deliver(n);
            n.setStatus(Notification.Status.SENT);
            n.setSentAt(Instant.now());
        } catch (RuntimeException ex) {
            log.warn("Notification delivery failed (attempt {}): {}", n.getAttempts(), ex.getMessage());
            n.setStatus(n.getAttempts() >= MAX_ATTEMPTS ? Notification.Status.FAILED : Notification.Status.PENDING);
        }
        notifications.save(n);
    }

    /** Retries anything still PENDING; called by the scheduler. */
    @Transactional
    public int retryPending() {
        List<Notification> pending = notifications.findByStatusAndAttemptsLessThan(Notification.Status.PENDING, MAX_ATTEMPTS);
        pending.forEach(this::send);
        return pending.size();
    }

    @Transactional(readOnly = true)
    public List<Notification> forUser(Long userId) {
        return notifications.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** The actual channel. Replace with email/SMS/push; throw to signal a delivery failure. */
    protected void deliver(Notification n) {
        log.info("[NOTIFY user={} type={}] {}", n.getUserId(), n.getType(), n.getMessage());
    }
}
