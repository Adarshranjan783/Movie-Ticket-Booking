package com.moviebooking.config;

import com.moviebooking.booking.Coupon;
import com.moviebooking.booking.CouponRepository;
import com.moviebooking.catalog.*;
import com.moviebooking.user.Role;
import com.moviebooking.user.UserRepository;
import com.moviebooking.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts a small, realistic data set on first start so you can hit the API immediately.
 * Disabled with booking.seed-data=false (tests do this).
 */
@Component
@ConditionalOnProperty(name = "booking.seed-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final UserService userService;
    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final ScreenRepository screens;
    private final SeatRepository seats;
    private final MovieRepository movies;
    private final ShowRepository shows;
    private final RefundPolicyRepository refundPolicies;
    private final CouponRepository coupons;

    public DemoDataSeeder(UserRepository users, UserService userService, CityRepository cities,
                          TheaterRepository theaters, ScreenRepository screens, SeatRepository seats,
                          MovieRepository movies, ShowRepository shows, RefundPolicyRepository refundPolicies,
                          CouponRepository coupons) {
        this.users = users;
        this.userService = userService;
        this.cities = cities;
        this.theaters = theaters;
        this.screens = screens;
        this.seats = seats;
        this.movies = movies;
        this.shows = shows;
        this.refundPolicies = refundPolicies;
        this.coupons = coupons;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        userService.register("admin@example.com", "admin123", "Platform Admin", Role.ADMIN);
        userService.register("customer@example.com", "customer123", "Demo Customer", Role.CUSTOMER);

        RefundPolicy standard = refundPolicies.save(new RefundPolicy("Standard", 24, 4, 50, true));

        City bengaluru = cities.save(new City("Bengaluru"));
        Theater pvr = theaters.save(new Theater(bengaluru, "PVR Forum Mall", "Koramangala"));
        Screen audi1 = screens.save(new Screen(pvr, "Audi 1"));
        List<Seat> grid = new ArrayList<>();
        for (char row = 'A'; row <= 'E'; row++) {
            SeatTier tier = row >= 'D' ? SeatTier.PREMIUM : SeatTier.REGULAR;
            for (int n = 1; n <= 8; n++) grid.add(new Seat(audi1, String.valueOf(row), n, tier));
        }
        seats.saveAll(grid);

        Movie movie = movies.save(new Movie("Interstellar", 169, "English", "Sci-Fi"));
        Instant tomorrowEvening = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
        Instant s1 = tomorrowEvening;
        Instant s2 = tomorrowEvening.plus(Duration.ofHours(4));
        shows.save(new Show(movie, audi1, s1, s1.plus(Duration.ofMinutes(169)),
                new BigDecimal("200.00"), new BigDecimal("350.00"), standard));
        shows.save(new Show(movie, audi1, s2, s2.plus(Duration.ofMinutes(169)),
                new BigDecimal("250.00"), new BigDecimal("400.00"), standard));

        coupons.save(new Coupon("WELCOME10", Coupon.DiscountType.PERCENT, new BigDecimal("10.00"),
                new BigDecimal("100.00"), Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(365))));

        log.info("Demo data seeded. Users: admin@example.com/admin123, customer@example.com/customer123");
    }
}
