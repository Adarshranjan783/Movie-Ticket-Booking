package com.moviebooking;

import com.moviebooking.catalog.CatalogAdminService;
import com.moviebooking.catalog.CatalogDtos.*;
import com.moviebooking.config.BookingProperties;
import com.moviebooking.user.Role;
import com.moviebooking.user.User;
import com.moviebooking.user.UserRepository;
import com.moviebooking.user.UserService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Builds a minimal city -> theater -> screen -> movie -> show graph for tests. */
@Component
public class TestCatalog {

    public record Fixture(Long cityId, Long theaterId, Long screenId, Long movieId, Long showId, Long refundPolicyId) {
    }

    private final CatalogAdminService admin;
    private final UserService userService;
    private final UserRepository users;
    private final BookingProperties props;

    public TestCatalog(CatalogAdminService admin, UserService userService, UserRepository users, BookingProperties props) {
        this.admin = admin;
        this.userService = userService;
        this.users = users;
        this.props = props;
    }

    public User user(String email, Role role) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> userService.register(email, "secret123", email, role));
    }

    /** A show ~2 days out on a weekday (so no weekend surcharge), 3 rows x 4 seats, row C premium, 200/350 prices. */
    public Fixture createShow() {
        return createShow(weekdayStart(Duration.ofDays(2)));
    }

    public Fixture createShow(Instant start) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Long policyId = admin.createRefundPolicy(new CreateRefundPolicyRequest("Std-" + tag, 24, 4, 50, true)).id();
        Long cityId = admin.createCity(new CreateCityRequest("City-" + tag)).id();
        Long theaterId = admin.createTheater(new CreateTheaterRequest(cityId, "Theater-" + tag, null)).id();
        Long screenId = admin.createScreen(new CreateScreenRequest(theaterId, "Screen 1", 3, 4, List.of("C"))).id();
        Long movieId = admin.createMovie(new CreateMovieRequest("Movie-" + tag, 120, "English", "Drama")).id();
        Long showId = admin.createShow(new CreateShowRequest(movieId, screenId, start,
                new BigDecimal("200.00"), new BigDecimal("350.00"), policyId)).id();
        return new Fixture(cityId, theaterId, screenId, movieId, showId, policyId);
    }

    public Instant weekdayStart(Duration fromNow) {
        ZonedDateTime t = Instant.now().plus(fromNow).atZone(props.zone());
        while (t.getDayOfWeek() == DayOfWeek.SATURDAY || t.getDayOfWeek() == DayOfWeek.SUNDAY) {
            t = t.plusDays(1);
        }
        return t.toInstant();
    }
}
