package com.vijaypurohit.movietickets.screening.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.screening.model.ScreeningSeatState;

public final class ScreeningBrowseResponses {
    private ScreeningBrowseResponses() { }

    public record ScreeningSummary(UUID id, UUID movieId, UUID auditoriumId, Instant startTime, Instant endTime) { }
    public record ScreeningDetails(UUID id, UUID movieId, UUID auditoriumId, Instant startTime, Instant endTime,
            List<PriceSummary> prices, int inventorySize) { }
    public record PriceSummary(SeatCategory category, BigDecimal amount, String currency) { }
    public record SeatAvailability(UUID screeningSeatId, UUID seatId, String rowLabel, int seatNumber,
            SeatCategory category, ScreeningSeatState state) { }
}
