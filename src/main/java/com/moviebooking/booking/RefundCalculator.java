package com.moviebooking.booking;

import com.moviebooking.catalog.RefundPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** Pure refund arithmetic, separated so the policy rules can be unit-tested without a database. */
public final class RefundCalculator {

    private RefundCalculator() {
    }

    public record Result(int percent, BigDecimal amount) {
    }

    /**
     * @param policy    applicable policy; null means "no refund policy configured" -> no refund
     * @param showStart when the show starts
     * @param now       cancellation time
     * @param paid      amount originally paid
     */
    public static Result calculate(RefundPolicy policy, Instant showStart, Instant now, BigDecimal paid) {
        if (policy == null || paid.signum() <= 0) {
            return new Result(0, BigDecimal.ZERO.setScale(2));
        }
        long hoursBefore = Duration.between(now, showStart).toHours();
        int percent = policy.refundPercentFor(hoursBefore);
        BigDecimal amount = paid.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Result(percent, amount);
    }
}
