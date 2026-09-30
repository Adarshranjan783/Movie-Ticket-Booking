package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.*;
import com.moviebooking.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Customer-facing read APIs: City -> Movie -> Theater -> Show. Seat availability lives in the inventory module. */
@Service
@Transactional(readOnly = true)
public class DiscoveryService {

    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final MovieRepository movies;
    private final ShowRepository shows;

    public DiscoveryService(CityRepository cities, TheaterRepository theaters, MovieRepository movies,
                            ShowRepository shows) {
        this.cities = cities;
        this.theaters = theaters;
        this.movies = movies;
        this.shows = shows;
    }

    public List<CityResponse> listCities() {
        return cities.findAll().stream().map(CityResponse::from).toList();
    }

    public List<TheaterResponse> listTheaters(Long cityId) {
        requireCity(cityId);
        return theaters.findByCityIdOrderByName(cityId).stream().map(TheaterResponse::from).toList();
    }

    public List<MovieResponse> listMovies(Long cityId) {
        if (cityId == null) {
            return movies.findAll().stream().map(MovieResponse::from).toList();
        }
        requireCity(cityId);
        return movies.findPlayingInCity(cityId, Instant.now()).stream().map(MovieResponse::from).toList();
    }

    /** Shows in a city, optionally filtered by movie and calendar date (UTC). Defaults to upcoming 7 days. */
    public List<ShowResponse> searchShows(Long cityId, Long movieId, LocalDate date) {
        requireCity(cityId);
        Instant from;
        Instant to;
        if (date != null) {
            from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            // never list shows that already started
            if (from.isBefore(Instant.now())) from = Instant.now();
        } else {
            from = Instant.now();
            to = from.plusSeconds(7 * 24 * 3600);
        }
        return shows.search(cityId, movieId, from, to).stream().map(ShowResponse::from).toList();
    }

    public ShowResponse getShow(Long showId) {
        return ShowResponse.from(shows.findByIdWithDetails(showId)
                .orElseThrow(() -> new ApiException.NotFound("Show", showId)));
    }

    private void requireCity(Long cityId) {
        if (cityId == null || !cities.existsById(cityId)) {
            throw new ApiException.NotFound("City", cityId);
        }
    }
}
