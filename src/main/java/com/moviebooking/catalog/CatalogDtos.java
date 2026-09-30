package com.moviebooking.catalog;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request/response records for the catalog APIs. Entities are never exposed directly. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---- Requests (admin) --------------------------------------------------

    public record CreateCityRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record CreateTheaterRequest(@NotNull Long cityId,
                                       @NotBlank @Size(max = 150) String name,
                                       @Size(max = 300) String address) {
    }

    /**
     * Creates a screen and generates its seat grid in one call.
     * Rows are labelled A, B, C...; rows listed in premiumRows get the PREMIUM tier.
     */
    public record CreateScreenRequest(@NotNull Long theaterId,
                                      @NotBlank @Size(max = 50) String name,
                                      @Min(1) @Max(26) int rows,
                                      @Min(1) @Max(50) int seatsPerRow,
                                      List<String> premiumRows) {
    }

    public record CreateMovieRequest(@NotBlank @Size(max = 200) String title,
                                     @Min(1) @Max(600) int durationMinutes,
                                     @Size(max = 50) String language,
                                     @Size(max = 50) String genre) {
    }

    public record CreateShowRequest(@NotNull Long movieId,
                                    @NotNull Long screenId,
                                    @NotNull Instant startTime,
                                    @NotNull @DecimalMin("0.00") BigDecimal regularPrice,
                                    @NotNull @DecimalMin("0.00") BigDecimal premiumPrice,
                                    Long refundPolicyId) {
    }

    public record CreateRefundPolicyRequest(@NotBlank @Size(max = 100) String name,
                                            @Min(0) int fullRefundHoursBefore,
                                            @Min(0) int partialRefundHoursBefore,
                                            @Min(0) @Max(100) int partialRefundPercent,
                                            boolean defaultPolicy) {
    }

    // ---- Responses ---------------------------------------------------------

    public record CityResponse(Long id, String name) {
        public static CityResponse from(City c) {
            return new CityResponse(c.getId(), c.getName());
        }
    }

    public record TheaterResponse(Long id, Long cityId, String name, String address) {
        public static TheaterResponse from(Theater t) {
            return new TheaterResponse(t.getId(), t.getCity().getId(), t.getName(), t.getAddress());
        }
    }

    public record ScreenResponse(Long id, Long theaterId, String name, long seatCount) {
    }

    public record MovieResponse(Long id, String title, int durationMinutes, String language, String genre) {
        public static MovieResponse from(Movie m) {
            return new MovieResponse(m.getId(), m.getTitle(), m.getDurationMinutes(), m.getLanguage(), m.getGenre());
        }
    }

    public record RefundPolicyResponse(Long id, String name, int fullRefundHoursBefore,
                                       int partialRefundHoursBefore, int partialRefundPercent, boolean defaultPolicy) {
        public static RefundPolicyResponse from(RefundPolicy p) {
            return new RefundPolicyResponse(p.getId(), p.getName(), p.getFullRefundHoursBefore(),
                    p.getPartialRefundHoursBefore(), p.getPartialRefundPercent(), p.isDefaultPolicy());
        }
    }

    /** Show with enough context (movie, theater, city) for a listing. Requires the fetch-joined queries. */
    public record ShowResponse(Long id, Long movieId, String movieTitle, Long screenId, String screenName,
                               Long theaterId, String theaterName, Long cityId, String cityName,
                               Instant startTime, Instant endTime,
                               BigDecimal regularPrice, BigDecimal premiumPrice, Long refundPolicyId) {
        public static ShowResponse from(Show s) {
            Screen sc = s.getScreen();
            Theater t = sc.getTheater();
            return new ShowResponse(s.getId(), s.getMovie().getId(), s.getMovie().getTitle(),
                    sc.getId(), sc.getName(), t.getId(), t.getName(), t.getCity().getId(), t.getCity().getName(),
                    s.getStartTime(), s.getEndTime(), s.getRegularPrice(), s.getPremiumPrice(),
                    s.getRefundPolicy() == null ? null : s.getRefundPolicy().getId());
        }
    }
}
