package com.moviebooking.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByBookingIdAndType(Long bookingId, Notification.Type type);

    List<Notification> findByStatusAndAttemptsLessThan(Notification.Status status, int maxAttempts);
}
