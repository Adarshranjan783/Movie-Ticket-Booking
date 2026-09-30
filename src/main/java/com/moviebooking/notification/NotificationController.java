package com.moviebooking.notification;

import com.moviebooking.user.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record NotificationResponse(Long id, Long bookingId, Notification.Type type, String message,
                                       Notification.Status status, Instant createdAt, Instant sentAt) {
    }

    /** The customer's own notification feed; handy for seeing async delivery without an email server. */
    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal AppUserDetails user) {
        return notificationService.forUser(user.getId()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getBookingId(), n.getType(), n.getMessage(),
                        n.getStatus(), n.getCreatedAt(), n.getSentAt()))
                .toList();
    }
}
