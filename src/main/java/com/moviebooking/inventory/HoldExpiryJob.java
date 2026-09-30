package com.moviebooking.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic sweep that deletes expired holds. Availability is already correct without it (expired holds are
 * treated as free and cleared on the next hold attempt); the sweep just keeps the table small.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final SeatHoldService holds;

    public HoldExpiryJob(SeatHoldService holds) {
        this.holds = holds;
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    public void sweep() {
        int released = holds.expireHolds();
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
