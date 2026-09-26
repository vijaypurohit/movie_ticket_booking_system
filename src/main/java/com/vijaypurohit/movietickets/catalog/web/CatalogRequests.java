package com.vijaypurohit.movietickets.catalog.web;

import java.util.UUID;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogRequests {
    private CatalogRequests() { }

    public record CityRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String country,
            @NotBlank @Size(max = 80) String timeZone) { }

    public record TheaterRequest(
            @NotNull UUID cityId,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 500) String address) { }

    public record AuditoriumRequest(@NotBlank @Size(max = 120) String name) { }

    public record SeatRequest(
            @NotBlank @Size(max = 10) String rowLabel,
            @Min(1) @Max(1000) int seatNumber,
            @NotNull SeatCategory category) { }

    public record MovieRequest(
            @NotBlank @Size(max = 240) String title,
            @Min(1) @Max(1440) int durationMinutes,
            @NotBlank @Size(max = 80) String language) { }
}
