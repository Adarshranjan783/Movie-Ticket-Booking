package com.moviebooking.booking;

import java.math.BigDecimal;

/** Application events published after booking state changes. Consumed asynchronously by the notification module. */
public final class BookingEvents {

    private BookingEvents() {
    }

    public record BookingConfirmed(Long bookingId, Long userId, String bookingRef, String movieTitle,
                                   String seatLabels, BigDecimal amountPaid) {
    }

    public record BookingCancelled(Long bookingId, Long userId, String bookingRef, String movieTitle,
                                   BigDecimal refundAmount, boolean refundSucceeded) {
    }
}
