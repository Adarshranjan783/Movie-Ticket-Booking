package com.moviebooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marks PENDING_PAYMENT bookings whose hold ran out as EXPIRED so they don't linger forever. */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void sweep() {
        int expired = bookingService.expireAbandonedBookings();
        if (expired > 0) {
            log.info("Expired {} unpaid booking(s)", expired);
        }
    }
}
