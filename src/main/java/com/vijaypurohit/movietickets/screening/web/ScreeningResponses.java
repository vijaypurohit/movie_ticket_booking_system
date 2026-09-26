package com.vijaypurohit.movietickets.screening.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;

public final class ScreeningResponses {
    private ScreeningResponses() { }

    public record ScreeningResponse(UUID id, UUID movieId, UUID auditoriumId, UUID pricingPlanId,
            UUID refundPolicyId, Instant startTime, Instant endTime, ScreeningStatus status,
            List<ScreeningPriceResponse> prices, int inventorySize, Instant createdAt, Instant updatedAt, long version) { }

    public record ScreeningPriceResponse(SeatCategory seatCategory, BigDecimal amount, String currency) { }
}
