package com.vijaypurohit.movietickets.catalog.web;

import java.util.UUID;

public final class BrowseResponses {
    private BrowseResponses() { }

    public record CitySummary(UUID id, String name, String country, String timeZone) { }
    public record TheaterSummary(UUID id, UUID cityId, String name, String address) { }
    public record MovieSummary(UUID id, String title, int durationMinutes, String language) { }
}
