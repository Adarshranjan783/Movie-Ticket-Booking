package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A screening of a movie on a specific screen at a specific time, with its own price per seat tier. */
@Entity
@Table(name = "shows", indexes = {
        @Index(name = "idx_show_screen_start", columnList = "screen_id, start_time"),
        @Index(name = "idx_show_movie_start", columnList = "movie_id, start_time")
})
@Getter
@Setter
@NoArgsConstructor
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal regularPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premiumPrice;

    /** Optional override; when null the default policy applies. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_policy_id")
    private RefundPolicy refundPolicy;

    public Show(Movie movie, Screen screen, Instant startTime, Instant endTime,
                BigDecimal regularPrice, BigDecimal premiumPrice, RefundPolicy refundPolicy) {
        this.movie = movie;
        this.screen = screen;
        this.startTime = startTime;
        this.endTime = endTime;
        this.regularPrice = regularPrice;
        this.premiumPrice = premiumPrice;
        this.refundPolicy = refundPolicy;
    }

    public BigDecimal priceFor(SeatTier tier) {
        return tier == SeatTier.PREMIUM ? premiumPrice : regularPrice;
    }
}
