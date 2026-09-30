package com.moviebooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Tunables for the booking flow, bound from the "booking.*" keys in application.yml.
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
        @DefaultValue("5") int holdDurationMinutes,
        @DefaultValue("20") int weekendSurchargePercent,
        @DefaultValue("24") int reminderHoursBeforeShow,
        @DefaultValue("Asia/Kolkata") String timeZone,
        @DefaultValue("true") boolean seedData
) {
    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
