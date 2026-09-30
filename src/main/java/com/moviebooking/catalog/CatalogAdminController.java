package com.moviebooking.catalog;

import com.moviebooking.catalog.CatalogDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-only catalog management. Access is enforced by the /api/admin/** rule in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class CatalogAdminController {

    private final CatalogAdminService service;

    public CatalogAdminController(CatalogAdminService service) {
        this.service = service;
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CreateCityRequest req) {
        return service.createCity(req);
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody CreateTheaterRequest req) {
        return service.createTheater(req);
    }

    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@Valid @RequestBody CreateScreenRequest req) {
        return service.createScreen(req);
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody CreateMovieRequest req) {
        return service.createMovie(req);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody CreateShowRequest req) {
        return service.createShow(req);
    }

    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundPolicyResponse createRefundPolicy(@Valid @RequestBody CreateRefundPolicyRequest req) {
        return service.createRefundPolicy(req);
    }

    @GetMapping("/refund-policies")
    public List<RefundPolicyResponse> listRefundPolicies() {
        return service.listRefundPolicies();
    }
}
