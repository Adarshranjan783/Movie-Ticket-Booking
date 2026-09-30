package com.moviebooking.inventory;

import com.moviebooking.catalog.SeatTier;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public enum SeatAvailability {
        AVAILABLE, HELD, BOOKED
    }

    public record SeatView(Long seatId, String row, int number, String label, SeatTier tier,
                           BigDecimal basePrice, SeatAvailability availability) {
    }

    public record SeatMapResponse(Long showId, int totalSeats, int availableSeats, List<SeatView> seats) {
    }

    public record HoldRequest(@NotEmpty @Size(max = 10) List<Long> seatIds) {
    }

    public record HoldResponse(String holdId, Long showId, List<String> seatLabels, Instant expiresAt) {
    }
}
