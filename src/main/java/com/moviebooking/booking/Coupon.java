package com.moviebooking.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "coupons", uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    public enum DiscountType {
        PERCENT, FLAT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DiscountType type;

    /** Percentage (0-100) for PERCENT, currency amount for FLAT. ("value" is a reserved word in SQL.) */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Optional cap for PERCENT coupons. */
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    @Column(nullable = false)
    private boolean active = true;

    public Coupon(String code, DiscountType type, BigDecimal value, BigDecimal maxDiscount,
                  Instant validFrom, Instant validTo) {
        this.code = code.toUpperCase();
        this.type = type;
        this.value = value;
        this.maxDiscount = maxDiscount;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isUsableAt(Instant now) {
        return active && !now.isBefore(validFrom) && now.isBefore(validTo);
    }

    /** Discount for a subtotal; never exceeds the subtotal. */
    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal discount = switch (type) {
            case PERCENT -> subtotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FLAT -> value;
        };
        if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) discount = maxDiscount;
        if (discount.compareTo(subtotal) > 0) discount = subtotal;
        return discount.setScale(2, RoundingMode.HALF_UP);
    }
}
