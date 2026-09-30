package com.moviebooking.notification;

import com.moviebooking.booking.Booking;
import com.moviebooking.booking.BookingRepository;
import com.moviebooking.booking.BookingStatus;
import com.moviebooking.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Sends one SHOW_REMINDER per confirmed booking whose show starts within the configured window. */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final BookingRepository bookings;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final BookingProperties props;

    public ReminderJob(BookingRepository bookings, NotificationRepository notifications,
                       NotificationService notificationService, BookingProperties props) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @Transactional
    public void sendReminders() {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofHours(props.reminderHoursBeforeShow()));
        int sent = 0;
        for (Booking b : bookings.findByStatusAndShowStartBetween(BookingStatus.CONFIRMED, now, until)) {
            if (notifications.existsByBookingIdAndType(b.getId(), Notification.Type.SHOW_REMINDER)) continue;
            notificationService.send(new Notification(b.getUser().getId(), b.getId(), Notification.Type.SHOW_REMINDER,
                    "Reminder: %s starts at %s. Booking %s."
                            .formatted(b.getShow().getMovie().getTitle(), b.getShow().getStartTime(), b.getBookingRef())));
            sent++;
        }
        int retried = notificationService.retryPending();
        if (sent > 0 || retried > 0) {
            log.info("Reminder job: {} reminder(s) sent, {} pending notification(s) retried", sent, retried);
        }
    }
}
