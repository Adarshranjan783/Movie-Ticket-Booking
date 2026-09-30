package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.CouponResponse;
import com.moviebooking.booking.BookingDtos.CreateCouponRequest;
import com.moviebooking.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class CouponService {

    private final CouponRepository coupons;

    public CouponService(CouponRepository coupons) {
        this.coupons = coupons;
    }

    /** Returns the coupon if it can be applied right now, otherwise throws a 400 with a clear reason. */
    @Transactional(readOnly = true)
    public Coupon requireUsable(String code) {
        Coupon coupon = coupons.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new ApiException.BadRequest("Unknown coupon code: " + code));
        if (!coupon.isUsableAt(Instant.now())) {
            throw new ApiException.BadRequest("Coupon is not valid right now: " + coupon.getCode());
        }
        return coupon;
    }

    @Transactional
    public CouponResponse create(CreateCouponRequest req) {
        if (coupons.findByCodeIgnoreCase(req.code()).isPresent()) {
            throw new ApiException.Conflict("DUPLICATE", "Coupon already exists: " + req.code());
        }
        if (!req.validTo().isAfter(req.validFrom())) {
            throw new ApiException.BadRequest("validTo must be after validFrom");
        }
        if (req.type() == Coupon.DiscountType.PERCENT && req.value().intValue() > 100) {
            throw new ApiException.BadRequest("Percent discount cannot exceed 100");
        }
        return CouponResponse.from(coupons.save(new Coupon(req.code().trim(), req.type(), req.value(),
                req.maxDiscount(), req.validFrom(), req.validTo())));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> list() {
        return coupons.findAll().stream().map(CouponResponse::from).toList();
    }
}
