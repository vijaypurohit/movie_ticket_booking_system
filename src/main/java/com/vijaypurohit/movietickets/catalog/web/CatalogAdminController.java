package com.vijaypurohit.movietickets.catalog.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.catalog.application.CatalogAdminService;
import com.vijaypurohit.movietickets.catalog.web.CatalogRequests.AuditoriumRequest;
import com.vijaypurohit.movietickets.catalog.web.CatalogRequests.CityRequest;
import com.vijaypurohit.movietickets.catalog.web.CatalogRequests.MovieRequest;
import com.vijaypurohit.movietickets.catalog.web.CatalogRequests.SeatRequest;
import com.vijaypurohit.movietickets.catalog.web.CatalogRequests.TheaterRequest;
import com.vijaypurohit.movietickets.catalog.web.CatalogResponses.AuditoriumResponse;
import com.vijaypurohit.movietickets.catalog.web.CatalogResponses.CityResponse;
import com.vijaypurohit.movietickets.catalog.web.CatalogResponses.MovieResponse;
import com.vijaypurohit.movietickets.catalog.web.CatalogResponses.SeatResponse;
import com.vijaypurohit.movietickets.catalog.web.CatalogResponses.TheaterResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping(value = "/admin/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin catalog")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogAdminController {
    private final CatalogAdminService service;
    public CatalogAdminController(CatalogAdminService service) { this.service = service; }

    @PostMapping("/cities") @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest request) { return service.createCity(request); }
    @GetMapping("/cities/{id}") public CityResponse getCity(@PathVariable UUID id) { return service.getCity(id); }
    @GetMapping("/cities") public PageResponse<CityResponse> listCities(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listCities(page, size); }
    @PutMapping("/cities/{id}") public CityResponse updateCity(@PathVariable UUID id, @Valid @RequestBody CityRequest request) { return service.updateCity(id, request); }
    @DeleteMapping("/cities/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateCity(@PathVariable UUID id) { service.deactivateCity(id); }

    @PostMapping("/theaters") @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody TheaterRequest request) { return service.createTheater(request); }
    @GetMapping("/theaters/{id}") public TheaterResponse getTheater(@PathVariable UUID id) { return service.getTheater(id); }
    @GetMapping("/theaters") public PageResponse<TheaterResponse> listTheaters(@RequestParam UUID cityId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listTheaters(cityId, page, size); }
    @PutMapping("/theaters/{id}") public TheaterResponse updateTheater(@PathVariable UUID id, @Valid @RequestBody TheaterRequest request) { return service.updateTheater(id, request); }
    @DeleteMapping("/theaters/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateTheater(@PathVariable UUID id) { service.deactivateTheater(id); }

    @PostMapping("/theaters/{theaterId}/auditoriums") @ResponseStatus(HttpStatus.CREATED)
    public AuditoriumResponse createAuditorium(@PathVariable UUID theaterId, @Valid @RequestBody AuditoriumRequest request) { return service.createAuditorium(theaterId, request); }
    @GetMapping("/auditoriums/{id}") public AuditoriumResponse getAuditorium(@PathVariable UUID id) { return service.getAuditorium(id); }
    @GetMapping("/theaters/{theaterId}/auditoriums") public PageResponse<AuditoriumResponse> listAuditoriums(@PathVariable UUID theaterId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listAuditoriums(theaterId, page, size); }
    @PutMapping("/auditoriums/{id}") public AuditoriumResponse updateAuditorium(@PathVariable UUID id, @Valid @RequestBody AuditoriumRequest request) { return service.updateAuditorium(id, request); }
    @DeleteMapping("/auditoriums/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateAuditorium(@PathVariable UUID id) { service.deactivateAuditorium(id); }

    @PostMapping("/auditoriums/{auditoriumId}/seats") @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse createSeat(@PathVariable UUID auditoriumId, @Valid @RequestBody SeatRequest request) { return service.createSeat(auditoriumId, request); }
    @GetMapping("/seats/{id}") public SeatResponse getSeat(@PathVariable UUID id) { return service.getSeat(id); }
    @GetMapping("/auditoriums/{auditoriumId}/seats") public PageResponse<SeatResponse> listSeats(@PathVariable UUID auditoriumId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listSeats(auditoriumId, page, size); }
    @PutMapping("/seats/{id}") public SeatResponse updateSeat(@PathVariable UUID id, @Valid @RequestBody SeatRequest request) { return service.updateSeat(id, request); }
    @DeleteMapping("/seats/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateSeat(@PathVariable UUID id) { service.deactivateSeat(id); }

    @PostMapping("/movies") @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) { return service.createMovie(request); }
    @GetMapping("/movies/{id}") public MovieResponse getMovie(@PathVariable UUID id) { return service.getMovie(id); }
    @GetMapping("/movies") public PageResponse<MovieResponse> listMovies(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listMovies(page, size); }
    @PutMapping("/movies/{id}") public MovieResponse updateMovie(@PathVariable UUID id, @Valid @RequestBody MovieRequest request) { return service.updateMovie(id, request); }
    @DeleteMapping("/movies/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateMovie(@PathVariable UUID id) { service.deactivateMovie(id); }
}
