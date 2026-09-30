package com.moviebooking.booking;

/**
 * Explicit booking lifecycle.
 * <pre>
 *   PENDING_PAYMENT --(payment success)--> CONFIRMED --(cancel)--> CANCELLED
 *   PENDING_PAYMENT --(hold expires)-----> EXPIRED
 * </pre>
 * A failed payment keeps the booking in PENDING_PAYMENT so the customer can retry while the hold is alive.
 */
public enum BookingStatus {
    PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED;

    public boolean canTransitionTo(BookingStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == CONFIRMED || next == EXPIRED;
            case CONFIRMED -> next == CANCELLED;
            case CANCELLED, EXPIRED -> false;
        };
    }
}
