package com.vijaypurohit.movietickets.screening.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public final class ScreeningRequests {
    private ScreeningRequests() { }

    public record CreateScreeningRequest(
            @NotNull UUID movieId,
            @NotNull UUID auditoriumId,
            @NotNull UUID pricingPlanId,
            @NotNull UUID refundPolicyId,
            @NotNull Instant startTime,
            @NotNull Instant endTime) { }
}
