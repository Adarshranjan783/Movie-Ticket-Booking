package com.moviebooking.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_seat_position",
                columnNames = {"screen_id", "row_label", "seat_number"}),
        indexes = @Index(name = "idx_seat_screen", columnList = "screen_id"))
@Getter
@Setter
@NoArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "row_label", nullable = false, length = 5)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatTier tier;

    public Seat(Screen screen, String rowLabel, int seatNumber, SeatTier tier) {
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.tier = tier;
    }

    /** Human-readable label such as "A10". */
    public String label() {
        return rowLabel + seatNumber;
    }
}
