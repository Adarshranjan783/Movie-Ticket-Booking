package com.moviebooking.booking;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatTier;
import com.moviebooking.catalog.Show;
import com.moviebooking.config.BookingProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Deterministic price calculation: tier price from the show, weekend surcharge, then coupon discount.
 * Pure function of its inputs so it is trivially unit-testable.
 */
@Service
public class PricingService {

    public record PriceLine(Long seatId, String label, SeatTier tier, BigDecimal basePrice,
                            BigDecimal weekendSurcharge, BigDecimal price) {
    }

    public record Quote(List<PriceLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal total,
                        String couponCode, boolean weekend) {
    }

    private final BookingProperties props;

    public PricingService(BookingProperties props) {
        this.props = props;
    }

    public Quote quote(Show show, List<Seat> seats, Coupon coupon) {
        boolean weekend = isWeekend(show);
        BigDecimal surchargeRate = weekend
                ? BigDecimal.valueOf(props.weekendSurchargePercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal subtotal = BigDecimal.ZERO;
        List<PriceLine> lines = seats.stream().map(seat -> {
            BigDecimal base = show.priceFor(seat.getTier());
            BigDecimal surcharge = base.multiply(surchargeRate).setScale(2, RoundingMode.HALF_UP);
            return new PriceLine(seat.getId(), seat.label(), seat.getTier(), base, surcharge, base.add(surcharge));
        }).toList();
        for (PriceLine line : lines) subtotal = subtotal.add(line.price());
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = coupon == null ? BigDecimal.ZERO.setScale(2) : coupon.discountFor(subtotal);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        return new Quote(lines, subtotal, discount, total, coupon == null ? null : coupon.getCode(), weekend);
    }

    boolean isWeekend(Show show) {
        DayOfWeek day = show.getStartTime().atZone(props.zone()).getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
