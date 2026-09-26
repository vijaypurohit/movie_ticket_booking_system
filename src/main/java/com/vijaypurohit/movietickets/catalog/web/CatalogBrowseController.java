package com.vijaypurohit.movietickets.catalog.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.catalog.application.CatalogBrowseService;
import com.vijaypurohit.movietickets.catalog.web.BrowseResponses.CitySummary;
import com.vijaypurohit.movietickets.catalog.web.BrowseResponses.MovieSummary;
import com.vijaypurohit.movietickets.catalog.web.BrowseResponses.TheaterSummary;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

import jakarta.validation.constraints.NotNull;

@Validated
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogBrowseController {
    private final CatalogBrowseService service;

    public CatalogBrowseController(CatalogBrowseService service) { this.service = service; }

    @GetMapping("/cities") public PageResponse<CitySummary> cities(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.cities(page, size); }
    @GetMapping("/theaters") public PageResponse<TheaterSummary> theaters(@RequestParam @NotNull UUID cityId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.theaters(cityId, page, size); }
    @GetMapping("/movies") public PageResponse<MovieSummary> movies(@RequestParam @NotNull UUID cityId, @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.movies(cityId, date, page, size); }
    @GetMapping("/movies/{id}") public MovieSummary movie(@PathVariable UUID id) { return service.movie(id); }
}
