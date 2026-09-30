package com.moviebooking.booking;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * In-memory stand-in for a real provider. Any payment method succeeds except one that starts with "FAIL",
 * which lets tests and manual demos exercise the failure path deterministically.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String paymentMethod) {
        if (paymentMethod != null && paymentMethod.toUpperCase().startsWith("FAIL")) {
            return new ChargeResult(false, null, "Card declined by simulated provider");
        }
        return new ChargeResult(true, "sim_pay_" + UUID.randomUUID(), null);
    }

    @Override
    public RefundResult refund(String paymentProviderRef, BigDecimal amount) {
        if (paymentProviderRef == null) {
            return new RefundResult(false, null);
        }
        return new RefundResult(true, "sim_ref_" + UUID.randomUUID());
    }
}
