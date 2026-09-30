package com.moviebooking.booking;

import com.moviebooking.catalog.RefundPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefundCalculatorTest {

    // full refund >= 24h before, 50% between 4h and 24h, nothing inside 4h
    private final RefundPolicy policy = new RefundPolicy("Standard", 24, 4, 50, true);
    private final BigDecimal paid = new BigDecimal("350.00");

    @Test
    void fullRefundWellBeforeShow() {
        Instant now = Instant.now();
        RefundCalculator.Result r = RefundCalculator.calculate(policy, now.plus(Duration.ofHours(48)), now, paid);
        assertThat(r.percent()).isEqualTo(100);
        assertThat(r.amount()).isEqualByComparingTo("350.00");
    }

    @Test
    void partialRefundInsideTheMiddleWindow() {
        Instant now = Instant.now();
        RefundCalculator.Result r = RefundCalculator.calculate(policy, now.plus(Duration.ofHours(10)), now, paid);
        assertThat(r.percent()).isEqualTo(50);
        assertThat(r.amount()).isEqualByComparingTo("175.00");
    }

    @Test
    void noRefundCloseToShowTime() {
        Instant now = Instant.now();
        RefundCalculator.Result r = RefundCalculator.calculate(policy, now.plus(Duration.ofHours(2)), now, paid);
        assertThat(r.percent()).isZero();
        assertThat(r.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void boundaryIsInclusive() {
        Instant now = Instant.now();
        // exactly 24h -> full; exactly 4h -> partial
        assertThat(RefundCalculator.calculate(policy, now.plus(Duration.ofHours(24)), now, paid).percent()).isEqualTo(100);
        assertThat(RefundCalculator.calculate(policy, now.plus(Duration.ofHours(4)), now, paid).percent()).isEqualTo(50);
    }

    @Test
    void noPolicyMeansNoRefund() {
        Instant now = Instant.now();
        RefundCalculator.Result r = RefundCalculator.calculate(null, now.plus(Duration.ofDays(5)), now, paid);
        assertThat(r.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void bookingStateMachineRejectsInvalidTransitions() {
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitionTo(BookingStatus.CANCELLED)).isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStatus.EXPIRED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
    }
}
