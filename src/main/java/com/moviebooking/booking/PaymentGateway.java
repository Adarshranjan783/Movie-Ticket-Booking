package com.moviebooking.booking;

import java.math.BigDecimal;

/**
 * Abstraction over a payment provider. The booking flow only depends on this interface, so a real
 * provider (Razorpay, Stripe, ...) can be plugged in later without touching BookingService.
 */
public interface PaymentGateway {

    record ChargeResult(boolean success, String providerRef, String failureReason) {
    }

    record RefundResult(boolean success, String providerRef) {
    }

    ChargeResult charge(String idempotencyKey, BigDecimal amount, String paymentMethod);

    RefundResult refund(String paymentProviderRef, BigDecimal amount);
}
