package com.moviebooking.booking;

import com.moviebooking.catalog.Seat;
import com.moviebooking.catalog.SeatTier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Historical record of which seats a booking covered and what each cost.
 * Live "is this seat taken" state is the inventory module's SeatAllocation, not this table.
 */
@Entity
@Table(name = "booking_seats",
        uniqueConstraints = @UniqueConstraint(name = "uk_booking_seat", columnNames = {"booking_id", "seat_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(name = "seat_label", nullable = false, length = 10)
    private String seatLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatTier tier;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public BookingSeat(Seat seat, BigDecimal price) {
        this.seat = seat;
        this.seatLabel = seat.label();
        this.tier = seat.getTier();
        this.price = price;
    }
}
