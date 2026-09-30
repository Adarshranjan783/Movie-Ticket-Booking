package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Public browse endpoints (no login required). */
@RestController
@RequestMapping("/api")
public class DiscoveryController {

    private final DiscoveryService discovery;

    public DiscoveryController(DiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/cities")
    public List<CityResponse> cities() {
        return discovery.listCities();
    }

    @GetMapping("/theaters")
    public List<TheaterResponse> theaters(@RequestParam Long cityId) {
        return discovery.listTheaters(cityId);
    }

    @GetMapping("/movies")
    public List<MovieResponse> movies(@RequestParam(required = false) Long cityId) {
        return discovery.listMovies(cityId);
    }

    @GetMapping("/shows")
    public List<ShowResponse> shows(@RequestParam Long cityId,
                                    @RequestParam(required = false) Long movieId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return discovery.searchShows(cityId, movieId, date);
    }

    @GetMapping("/shows/{showId}")
    public ShowResponse show(@PathVariable Long showId) {
        return discovery.getShow(showId);
    }
}
