package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.*;
import com.moviebooking.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Admin-side write operations for the catalog. Enforces the structural invariants (theater in city, etc.). */
@Service
@Transactional
public class CatalogAdminService {

    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final ScreenRepository screens;
    private final SeatRepository seats;
    private final MovieRepository movies;
    private final ShowRepository shows;
    private final RefundPolicyRepository refundPolicies;

    public CatalogAdminService(CityRepository cities, TheaterRepository theaters, ScreenRepository screens,
                               SeatRepository seats, MovieRepository movies, ShowRepository shows,
                               RefundPolicyRepository refundPolicies) {
        this.cities = cities;
        this.theaters = theaters;
        this.screens = screens;
        this.seats = seats;
        this.movies = movies;
        this.shows = shows;
        this.refundPolicies = refundPolicies;
    }

    public CityResponse createCity(CreateCityRequest req) {
        if (cities.existsByNameIgnoreCase(req.name())) {
            throw new ApiException.Conflict("DUPLICATE", "City already exists: " + req.name());
        }
        return CityResponse.from(cities.save(new City(req.name().trim())));
    }

    public TheaterResponse createTheater(CreateTheaterRequest req) {
        City city = cities.findById(req.cityId()).orElseThrow(() -> new ApiException.NotFound("City", req.cityId()));
        return TheaterResponse.from(theaters.save(new Theater(city, req.name().trim(), req.address())));
    }

    public ScreenResponse createScreen(CreateScreenRequest req) {
        Theater theater = theaters.findById(req.theaterId())
                .orElseThrow(() -> new ApiException.NotFound("Theater", req.theaterId()));
        Screen screen = screens.save(new Screen(theater, req.name().trim()));

        Set<String> premium = req.premiumRows() == null ? Set.of() : Set.copyOf(req.premiumRows());
        List<Seat> grid = new ArrayList<>();
        for (int r = 0; r < req.rows(); r++) {
            String row = String.valueOf((char) ('A' + r));
            SeatTier tier = premium.contains(row) ? SeatTier.PREMIUM : SeatTier.REGULAR;
            for (int n = 1; n <= req.seatsPerRow(); n++) {
                grid.add(new Seat(screen, row, n, tier));
            }
        }
        seats.saveAll(grid);
        return new ScreenResponse(screen.getId(), theater.getId(), screen.getName(), grid.size());
    }

    public MovieResponse createMovie(CreateMovieRequest req) {
        return MovieResponse.from(movies.save(
                new Movie(req.title().trim(), req.durationMinutes(), req.language(), req.genre())));
    }

    public ShowResponse createShow(CreateShowRequest req) {
        Movie movie = movies.findById(req.movieId()).orElseThrow(() -> new ApiException.NotFound("Movie", req.movieId()));
        Screen screen = screens.findById(req.screenId()).orElseThrow(() -> new ApiException.NotFound("Screen", req.screenId()));
        RefundPolicy policy = null;
        if (req.refundPolicyId() != null) {
            policy = refundPolicies.findById(req.refundPolicyId())
                    .orElseThrow(() -> new ApiException.NotFound("RefundPolicy", req.refundPolicyId()));
        }
        if (req.startTime().isBefore(Instant.now())) {
            throw new ApiException.BadRequest("Show start time must be in the future");
        }
        if (req.premiumPrice().compareTo(req.regularPrice()) < 0) {
            throw new ApiException.BadRequest("Premium price cannot be lower than regular price");
        }
        if (seats.countByScreenId(screen.getId()) == 0) {
            throw new ApiException.BadRequest("Screen has no seats configured");
        }

        Instant end = req.startTime().plus(Duration.ofMinutes(movie.getDurationMinutes()));
        if (shows.existsOverlapping(screen.getId(), req.startTime(), end)) {
            throw new ApiException.Conflict("SHOW_OVERLAP", "Another show is scheduled on this screen during that time");
        }

        Show show = shows.save(new Show(movie, screen, req.startTime(), end,
                req.regularPrice(), req.premiumPrice(), policy));
        return ShowResponse.from(shows.findByIdWithDetails(show.getId()).orElseThrow());
    }

    public RefundPolicyResponse createRefundPolicy(CreateRefundPolicyRequest req) {
        if (req.partialRefundHoursBefore() > req.fullRefundHoursBefore()) {
            throw new ApiException.BadRequest("partialRefundHoursBefore must be <= fullRefundHoursBefore");
        }
        if (req.defaultPolicy()) {
            refundPolicies.findFirstByDefaultPolicyTrue().ifPresent(p -> p.setDefaultPolicy(false));
        }
        return RefundPolicyResponse.from(refundPolicies.save(new RefundPolicy(req.name().trim(),
                req.fullRefundHoursBefore(), req.partialRefundHoursBefore(), req.partialRefundPercent(),
                req.defaultPolicy())));
    }

    @Transactional(readOnly = true)
    public List<RefundPolicyResponse> listRefundPolicies() {
        return refundPolicies.findAll().stream().map(RefundPolicyResponse::from).toList();
    }
}
