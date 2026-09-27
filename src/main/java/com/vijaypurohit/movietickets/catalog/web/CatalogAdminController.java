package com.vijaypurohit.movietickets.catalog.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.catalog.application.CatalogAdminService;
import com.vijaypurohit.movietickets.generated.api.AdminCatalogApi;
import com.vijaypurohit.movietickets.generated.model.AuditoriumRequest;
import com.vijaypurohit.movietickets.generated.model.AuditoriumResponse;
import com.vijaypurohit.movietickets.generated.model.CityRequest;
import com.vijaypurohit.movietickets.generated.model.CityResponse;
import com.vijaypurohit.movietickets.generated.model.MovieRequest;
import com.vijaypurohit.movietickets.generated.model.MovieResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseAuditoriumResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseCityResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseMovieResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseSeatResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseTheaterResponse;
import com.vijaypurohit.movietickets.generated.model.SeatRequest;
import com.vijaypurohit.movietickets.generated.model.SeatResponse;
import com.vijaypurohit.movietickets.generated.model.TheaterRequest;
import com.vijaypurohit.movietickets.generated.model.TheaterResponse;

@RestController
@ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogAdminController implements AdminCatalogApi {
    private final CatalogAdminService service;

    public CatalogAdminController(CatalogAdminService service) { this.service = service; }

    @Override
    public ResponseEntity<CityResponse> createCity(CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCity(request));
    }

    @Override
    public ResponseEntity<CityResponse> getCity(UUID id) {
        return ResponseEntity.ok(service.getCity(id));
    }

    @Override
    public ResponseEntity<PageResponseCityResponse> listCities(Integer page, Integer size) {
        var result = service.listCities(page, size);
        return ResponseEntity.ok(new PageResponseCityResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<CityResponse> updateCity(UUID id, CityRequest request) {
        return ResponseEntity.ok(service.updateCity(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateCity(UUID id) {
        service.deactivateCity(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TheaterResponse> createTheater(TheaterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTheater(request));
    }

    @Override
    public ResponseEntity<TheaterResponse> getTheater(UUID id) {
        return ResponseEntity.ok(service.getTheater(id));
    }

    @Override
    public ResponseEntity<PageResponseTheaterResponse> listTheaters(UUID cityId, Integer page, Integer size) {
        var result = service.listTheaters(cityId, page, size);
        return ResponseEntity.ok(new PageResponseTheaterResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<TheaterResponse> updateTheater(UUID id, TheaterRequest request) {
        return ResponseEntity.ok(service.updateTheater(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateTheater(UUID id) {
        service.deactivateTheater(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AuditoriumResponse> createAuditorium(UUID theaterId, AuditoriumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAuditorium(theaterId, request));
    }

    @Override
    public ResponseEntity<AuditoriumResponse> getAuditorium(UUID id) {
        return ResponseEntity.ok(service.getAuditorium(id));
    }

    @Override
    public ResponseEntity<PageResponseAuditoriumResponse> listAuditoriums(UUID theaterId, Integer page, Integer size) {
        var result = service.listAuditoriums(theaterId, page, size);
        return ResponseEntity.ok(new PageResponseAuditoriumResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<AuditoriumResponse> updateAuditorium(UUID id, AuditoriumRequest request) {
        return ResponseEntity.ok(service.updateAuditorium(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateAuditorium(UUID id) {
        service.deactivateAuditorium(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<SeatResponse> createSeat(UUID auditoriumId, SeatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSeat(auditoriumId, request));
    }

    @Override
    public ResponseEntity<SeatResponse> getSeat(UUID id) {
        return ResponseEntity.ok(service.getSeat(id));
    }

    @Override
    public ResponseEntity<PageResponseSeatResponse> listSeats(UUID auditoriumId, Integer page, Integer size) {
        var result = service.listSeats(auditoriumId, page, size);
        return ResponseEntity.ok(new PageResponseSeatResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<SeatResponse> updateSeat(UUID id, SeatRequest request) {
        return ResponseEntity.ok(service.updateSeat(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateSeat(UUID id) {
        service.deactivateSeat(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MovieResponse> createMovie(MovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMovie(request));
    }

    @Override
    public ResponseEntity<MovieResponse> getMovie(UUID id) {
        return ResponseEntity.ok(service.getMovie(id));
    }

    @Override
    public ResponseEntity<PageResponseMovieResponse> listMovies(Integer page, Integer size) {
        var result = service.listMovies(page, size);
        return ResponseEntity.ok(new PageResponseMovieResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<MovieResponse> updateMovie(UUID id, MovieRequest request) {
        return ResponseEntity.ok(service.updateMovie(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateMovie(UUID id) {
        service.deactivateMovie(id);
        return ResponseEntity.noContent().build();
    }
}
