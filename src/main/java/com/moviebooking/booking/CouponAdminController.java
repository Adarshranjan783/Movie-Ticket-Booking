package com.moviebooking.booking;

import com.moviebooking.booking.BookingDtos.CouponResponse;
import com.moviebooking.booking.BookingDtos.CreateCouponRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
public class CouponAdminController {

    private final CouponService coupons;

    public CouponAdminController(CouponService coupons) {
        this.coupons = coupons;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CreateCouponRequest req) {
        return coupons.create(req);
    }

    @GetMapping
    public List<CouponResponse> list() {
        return coupons.list();
    }
}
