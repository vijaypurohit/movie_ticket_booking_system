package com.vijaypurohit.movietickets.catalog.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.catalog.application.CatalogBrowseService;
import com.vijaypurohit.movietickets.generated.api.PublicCatalogApi;
import com.vijaypurohit.movietickets.generated.model.CitySummary;
import com.vijaypurohit.movietickets.generated.model.MovieSummary;
import com.vijaypurohit.movietickets.generated.model.PageResponseCitySummary;
import com.vijaypurohit.movietickets.generated.model.PageResponseMovieSummary;
import com.vijaypurohit.movietickets.generated.model.PageResponseTheaterSummary;
import com.vijaypurohit.movietickets.generated.model.TheaterSummary;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

@RestController
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogBrowseController implements PublicCatalogApi {
    private final CatalogBrowseService service;

    public CatalogBrowseController(CatalogBrowseService service) { this.service = service; }

    @Override
    public ResponseEntity<PageResponseCitySummary> browseCities(Integer page, Integer size) {
        PageResponse<CitySummary> result = service.cities(page, size);
        return ResponseEntity.ok(new PageResponseCitySummary()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<PageResponseTheaterSummary> browseTheaters(UUID cityId, Integer page, Integer size) {
        PageResponse<TheaterSummary> result = service.theaters(cityId, page, size);
        return ResponseEntity.ok(new PageResponseTheaterSummary()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<PageResponseMovieSummary> browseMovies(UUID cityId, LocalDate date, Integer page, Integer size) {
        PageResponse<MovieSummary> result = service.movies(cityId, date, page, size);
        return ResponseEntity.ok(new PageResponseMovieSummary()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<MovieSummary> browseMovie(UUID id) {
        return ResponseEntity.ok(service.movie(id));
    }
}
