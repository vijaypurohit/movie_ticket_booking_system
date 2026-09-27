package com.vijaypurohit.movietickets.catalog.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.catalog.model.City;
import com.vijaypurohit.movietickets.catalog.model.Movie;
import com.vijaypurohit.movietickets.catalog.persistence.CityRepository;
import com.vijaypurohit.movietickets.catalog.persistence.MovieRepository;
import com.vijaypurohit.movietickets.catalog.persistence.TheaterRepository;
import com.vijaypurohit.movietickets.generated.model.CitySummary;
import com.vijaypurohit.movietickets.generated.model.MovieSummary;
import com.vijaypurohit.movietickets.generated.model.TheaterSummary;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

@Service
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogBrowseService {
    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final MovieRepository movies;

    public CatalogBrowseService(CityRepository cities, TheaterRepository theaters, MovieRepository movies) {
        this.cities = cities; this.theaters = theaters; this.movies = movies;
    }

    @Transactional(readOnly = true)
    public PageResponse<CitySummary> cities(Integer page, Integer size) {
        var paging = PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size), Sort.by("name", "id"));
        return PageResponse.from(cities.findByActiveTrue(paging).map(this::city));
    }

    @Transactional(readOnly = true)
    public PageResponse<TheaterSummary> theaters(UUID cityId, Integer page, Integer size) {
        requireActiveCity(cityId);
        var paging = PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size), Sort.by("name", "id"));
        return PageResponse.from(theaters.findByCityIdAndActiveTrue(cityId, paging)
                .map(value -> new TheaterSummary()
                .id(value.getId())
                .cityId(cityId)
                .name(value.getName())
                .address(value.getAddress())));
    }

    @Transactional(readOnly = true)
    public PageResponse<MovieSummary> movies(UUID cityId, LocalDate date, Integer page, Integer size) {
        DayRange range = dayRange(cityId, date);
        var paging = PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size));
        return PageResponse.from(movies.findAvailable(cityId, range.start(), range.end(), paging).map(this::movie));
    }

    @Transactional(readOnly = true)
    public MovieSummary movie(UUID id) {
        Movie movie = movies.findById(id).filter(Movie::isActive).orElseThrow(this::missing);
        return movie(movie);
    }

    @Transactional(readOnly = true)
    public DayRange dayRange(UUID cityId, LocalDate date) {
        City city = requireActiveCity(cityId);
        ZoneId zone = ZoneId.of(city.getTimeZone());
        return new DayRange(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private City requireActiveCity(UUID id) { return cities.findById(id).filter(City::isActive).orElseThrow(this::missing); }
    private ResourceNotFoundException missing() { return new ResourceNotFoundException("not-found", "Resource not found", "RESOURCE_NOT_FOUND", "The requested resource was not found."); }
    private CitySummary city(City value) { return new CitySummary()
                .id(value.getId())
                .name(value.getName())
                .country(value.getCountry())
                .timeZone(value.getTimeZone()); }
    private MovieSummary movie(Movie value) { return new MovieSummary()
                .id(value.getId())
                .title(value.getTitle())
                .durationMinutes(value.getDurationMinutes())
                .language(value.getLanguage()); }

    public record DayRange(Instant start, Instant end) { }
}
