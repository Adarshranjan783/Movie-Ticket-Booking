package com.moviebooking.booking;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BookingDtos {

    private BookingDtos() {
    }

    // ---- Requests ----------------------------------------------------------

    public record CreateBookingRequest(@NotBlank String holdId, @Size(max = 40) String couponCode) {
    }

    /**
     * @param idempotencyKey client-generated unique key per payment attempt (e.g. a UUID). Resending the same
     *                       key returns the original outcome instead of charging twice.
     * @param paymentMethod  free-form for the simulated gateway; anything starting with "FAIL" is declined.
     */
    public record PayRequest(@NotBlank @Size(max = 80) String idempotencyKey,
                             @NotBlank @Size(max = 40) String paymentMethod) {
    }

    public record CreateCouponRequest(@NotBlank @Size(max = 40) String code,
                                      @NotNull Coupon.DiscountType type,
                                      @NotNull @DecimalMin("0.01") BigDecimal value,
                                      BigDecimal maxDiscount,
                                      @NotNull Instant validFrom,
                                      @NotNull Instant validTo) {
    }

    // ---- Responses ---------------------------------------------------------

    public record SeatLine(Long seatId, String label, String tier, BigDecimal price) {
    }

    public record PaymentInfo(String idempotencyKey, BigDecimal amount, Payment.Status status,
                              String failureReason, Instant createdAt) {
        static PaymentInfo from(Payment p) {
            return new PaymentInfo(p.getIdempotencyKey(), p.getAmount(), p.getStatus(), p.getFailureReason(), p.getCreatedAt());
        }
    }

    public record RefundInfo(BigDecimal amount, int percent, Refund.Status status) {
        static RefundInfo from(Refund r) {
            return new RefundInfo(r.getAmount(), r.getRefundPercent(), r.getStatus());
        }
    }

    public record BookingResponse(Long id, String bookingRef, BookingStatus status,
                                  Long showId, String movieTitle, Instant showStartTime,
                                  List<SeatLine> seats,
                                  BigDecimal subtotal, BigDecimal discount, BigDecimal total, String couponCode,
                                  Instant holdExpiresAt, PaymentInfo lastPayment, RefundInfo refund,
                                  Instant createdAt) {
    }

    public record CouponResponse(Long id, String code, Coupon.DiscountType type, BigDecimal value,
                                 BigDecimal maxDiscount, Instant validFrom, Instant validTo, boolean active) {
        static CouponResponse from(Coupon c) {
            return new CouponResponse(c.getId(), c.getCode(), c.getType(), c.getValue(), c.getMaxDiscount(),
                    c.getValidFrom(), c.getValidTo(), c.isActive());
        }
    }
}
