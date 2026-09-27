package com.vijaypurohit.movietickets.catalog.application;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.catalog.persistence.AuditoriumRepository;
import com.vijaypurohit.movietickets.catalog.persistence.MovieRepository;
import com.vijaypurohit.movietickets.catalog.persistence.SeatRepository;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogLookupService {
    private final AuditoriumRepository auditoriums;
    private final SeatRepository seats;
    private final MovieRepository movies;

    public CatalogLookupService(AuditoriumRepository auditoriums, SeatRepository seats, MovieRepository movies) {
        this.auditoriums = auditoriums;
        this.seats = seats;
        this.movies = movies;
    }

    public AuditoriumSnapshot lockActiveAuditorium(UUID id) {
        var auditorium = auditoriums.findByIdForUpdate(id).orElseThrow(() -> missing("Auditorium"));
        if (!auditorium.isActive() || !auditorium.getTheater().isActive() || !auditorium.getTheater().getCity().isActive()) {
            throw inactive("auditorium");
        }
        List<SeatSnapshot> activeSeats = seats.findByAuditoriumId(id, org.springframework.data.domain.Pageable.unpaged())
                .stream().filter(seat -> seat.isActive())
                .map(seat -> new SeatSnapshot(seat.getId(), seat.getCategory())).toList();
        return new AuditoriumSnapshot(id, ZoneId.of(auditorium.getTheater().getCity().getTimeZone()), activeSeats);
    }

    public void requireActiveMovie(UUID id) {
        var movie = movies.findById(id).orElseThrow(() -> missing("Movie"));
        if (!movie.isActive()) throw inactive("movie");
    }

    private ResourceNotFoundException missing(String resource) {
        return new ResourceNotFoundException("not-found", resource + " not found", "RESOURCE_NOT_FOUND", "The requested resource was not found.");
    }
    private BusinessRuleViolationException inactive(String resource) {
        return new BusinessRuleViolationException("inactive-catalog", "Inactive catalog resource", "INACTIVE_CATALOG_RESOURCE", "The selected " + resource + " is inactive.");
    }

    public record AuditoriumSnapshot(UUID id, ZoneId timeZone, List<SeatSnapshot> seats) { }
    public record SeatSnapshot(UUID id, SeatCategory category) { }
}
