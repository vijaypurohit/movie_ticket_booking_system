package com.vijaypurohit.movietickets.catalog.web;

import java.time.Instant;
import java.util.UUID;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;

public final class CatalogResponses {
    private CatalogResponses() { }

    public record CityResponse(UUID id, String name, String country, String timeZone, boolean active,
                               Instant createdAt, Instant updatedAt, long version) { }
    public record TheaterResponse(UUID id, UUID cityId, String name, String address, boolean active,
                                  Instant createdAt, Instant updatedAt, long version) { }
    public record AuditoriumResponse(UUID id, UUID theaterId, String name, boolean active,
                                     Instant createdAt, Instant updatedAt, long version) { }
    public record SeatResponse(UUID id, UUID auditoriumId, String rowLabel, int seatNumber,
                               SeatCategory category, boolean active, Instant createdAt, Instant updatedAt, long version) { }
    public record MovieResponse(UUID id, String title, int durationMinutes, String language, boolean active,
                                Instant createdAt, Instant updatedAt, long version) { }
}
