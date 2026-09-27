package com.vijaypurohit.movietickets.reservation.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vijaypurohit.movietickets.reservation.model.ReservationState;

public final class ReservationResponses {
    private ReservationResponses() { }
    public record ReservationResponse(UUID id, UUID screeningId, List<UUID> screeningSeatIds,
            ReservationState state, Instant expiresAt, Instant createdAt) { }
}
