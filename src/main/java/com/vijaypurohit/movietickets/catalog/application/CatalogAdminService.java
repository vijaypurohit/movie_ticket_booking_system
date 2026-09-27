package com.vijaypurohit.movietickets.catalog.application;

import java.time.DateTimeException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.catalog.model.Auditorium;
import com.vijaypurohit.movietickets.catalog.model.City;
import com.vijaypurohit.movietickets.catalog.model.Movie;
import com.vijaypurohit.movietickets.catalog.model.Seat;
import com.vijaypurohit.movietickets.catalog.model.Theater;
import com.vijaypurohit.movietickets.catalog.persistence.AuditoriumRepository;
import com.vijaypurohit.movietickets.catalog.persistence.CityRepository;
import com.vijaypurohit.movietickets.catalog.persistence.MovieRepository;
import com.vijaypurohit.movietickets.catalog.persistence.SeatRepository;
import com.vijaypurohit.movietickets.catalog.persistence.TheaterRepository;
import com.vijaypurohit.movietickets.generated.model.AuditoriumRequest;
import com.vijaypurohit.movietickets.generated.model.CityRequest;
import com.vijaypurohit.movietickets.generated.model.MovieRequest;
import com.vijaypurohit.movietickets.generated.model.SeatRequest;
import com.vijaypurohit.movietickets.generated.model.TheaterRequest;
import com.vijaypurohit.movietickets.generated.model.AuditoriumResponse;
import com.vijaypurohit.movietickets.generated.model.CityResponse;
import com.vijaypurohit.movietickets.generated.model.MovieResponse;
import com.vijaypurohit.movietickets.generated.model.SeatResponse;
import com.vijaypurohit.movietickets.generated.model.TheaterResponse;
import com.vijaypurohit.movietickets.shared.error.BadRequestException;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

@Service
@ConditionalOnProperty(prefix = "app.catalog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CatalogAdminService {
    private final CityRepository cities;
    private final TheaterRepository theaters;
    private final AuditoriumRepository auditoriums;
    private final SeatRepository seats;
    private final MovieRepository movies;
    private final IdGenerator ids;
    private final FutureScreeningChecker futureScreenings;
    private final Clock clock;

    public CatalogAdminService(CityRepository cities, TheaterRepository theaters,
            AuditoriumRepository auditoriums, SeatRepository seats, MovieRepository movies, IdGenerator ids,
            FutureScreeningChecker futureScreenings, Clock clock) {
        this.cities = cities; this.theaters = theaters; this.auditoriums = auditoriums;
        this.seats = seats; this.movies = movies; this.ids = ids;
        this.futureScreenings = futureScreenings; this.clock = clock;
    }

    @Transactional
    public CityResponse createCity(CityRequest request) {
        validateZone(request.getTimeZone());
        return city(cities.save(new City(ids.nextId(), request.getName(), request.getCountry(), request.getTimeZone())));
    }
    @Transactional(readOnly = true) public CityResponse getCity(UUID id) { return city(requireCity(id)); }
    @Transactional(readOnly = true) public PageResponse<CityResponse> listCities(Integer page, Integer size) {
        return PageResponse.from(cities.findAll(page(page, size, "name")).map(this::city));
    }
    @Transactional public CityResponse updateCity(UUID id, CityRequest request) { validateZone(request.getTimeZone()); City value = requireCity(id); value.update(request.getName(), request.getCountry(), request.getTimeZone()); return city(value); }
    @Transactional public void deactivateCity(UUID id) { requireCity(id).deactivate(); }

    @Transactional public TheaterResponse createTheater(TheaterRequest request) { return theater(theaters.save(new Theater(ids.nextId(), requireCity(request.getCityId()), request.getName(), request.getAddress()))); }
    @Transactional(readOnly = true) public TheaterResponse getTheater(UUID id) { return theater(requireTheater(id)); }
    @Transactional(readOnly = true) public PageResponse<TheaterResponse> listTheaters(UUID cityId, Integer page, Integer size) { requireCity(cityId); return PageResponse.from(theaters.findByCityId(cityId, page(page, size, "name")).map(this::theater)); }
    @Transactional public TheaterResponse updateTheater(UUID id, TheaterRequest request) { Theater value = requireTheater(id); if (!value.getCity().getId().equals(request.getCityId())) throw invalid("A theater cannot be moved to another city."); value.update(request.getName(), request.getAddress()); return theater(value); }
    @Transactional public void deactivateTheater(UUID id) { requireTheater(id).deactivate(); }

    @Transactional public AuditoriumResponse createAuditorium(UUID theaterId, AuditoriumRequest request) { return auditorium(auditoriums.save(new Auditorium(ids.nextId(), requireTheater(theaterId), request.getName()))); }
    @Transactional(readOnly = true) public AuditoriumResponse getAuditorium(UUID id) { return auditorium(requireAuditorium(id)); }
    @Transactional(readOnly = true) public PageResponse<AuditoriumResponse> listAuditoriums(UUID theaterId, Integer page, Integer size) { requireTheater(theaterId); return PageResponse.from(auditoriums.findByTheaterId(theaterId, page(page, size, "name")).map(this::auditorium)); }
    @Transactional public AuditoriumResponse updateAuditorium(UUID id, AuditoriumRequest request) { Auditorium value = requireAuditorium(id); value.update(request.getName()); return auditorium(value); }
    @Transactional public void deactivateAuditorium(UUID id) { requireAuditorium(id).deactivate(); }

    @Transactional public SeatResponse createSeat(UUID auditoriumId, SeatRequest request) { requireMutableLayout(auditoriumId); return seat(seats.save(new Seat(ids.nextId(), requireAuditorium(auditoriumId), request.getRowLabel(), request.getSeatNumber(), request.getCategory()))); }
    @Transactional(readOnly = true) public SeatResponse getSeat(UUID id) { return seat(requireSeat(id)); }
    @Transactional(readOnly = true) public PageResponse<SeatResponse> listSeats(UUID auditoriumId, Integer page, Integer size) { requireAuditorium(auditoriumId); return PageResponse.from(seats.findByAuditoriumId(auditoriumId, page(page, size, "rowLabel", "seatNumber")).map(this::seat)); }
    @Transactional public SeatResponse updateSeat(UUID id, SeatRequest request) { Seat value = requireSeat(id); requireMutableLayout(value.getAuditorium().getId()); value.update(request.getRowLabel(), request.getSeatNumber(), request.getCategory()); return seat(value); }
    @Transactional public void deactivateSeat(UUID id) { Seat value = requireSeat(id); requireMutableLayout(value.getAuditorium().getId()); value.deactivate(); }

    @Transactional public MovieResponse createMovie(MovieRequest request) { return movie(movies.save(new Movie(ids.nextId(), request.getTitle(), request.getDurationMinutes(), request.getLanguage()))); }
    @Transactional(readOnly = true) public MovieResponse getMovie(UUID id) { return movie(requireMovie(id)); }
    @Transactional(readOnly = true) public PageResponse<MovieResponse> listMovies(Integer page, Integer size) { return PageResponse.from(movies.findAll(page(page, size, "title")).map(this::movie)); }
    @Transactional public MovieResponse updateMovie(UUID id, MovieRequest request) { Movie value = requireMovie(id); value.update(request.getTitle(), request.getDurationMinutes(), request.getLanguage()); return movie(value); }
    @Transactional public void deactivateMovie(UUID id) { requireMovie(id).deactivate(); }

    private PageRequest page(Integer page, Integer size, String... properties) { return PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size), Sort.by(properties).ascending().and(Sort.by("id"))); }
    private City requireCity(UUID id) { return cities.findById(id).orElseThrow(() -> missing("city")); }
    private Theater requireTheater(UUID id) { return theaters.findById(id).orElseThrow(() -> missing("theater")); }
    private Auditorium requireAuditorium(UUID id) { return auditoriums.findById(id).orElseThrow(() -> missing("auditorium")); }
    private Seat requireSeat(UUID id) { return seats.findById(id).orElseThrow(() -> missing("seat")); }
    private Movie requireMovie(UUID id) { return movies.findById(id).orElseThrow(() -> missing("movie")); }
    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException(resource + "-not-found", "Resource not found", "RESOURCE_NOT_FOUND", "The requested " + resource + " was not found."); }
    private BadRequestException invalid(String detail) { return new BadRequestException("invalid-catalog-request", "Invalid catalog request", "INVALID_CATALOG_REQUEST", detail); }
    private void requireMutableLayout(UUID auditoriumId) { if (futureScreenings.hasFutureScreening(auditoriumId, clock.instant())) throw new BusinessRuleViolationException("seat-layout-in-use", "Seat layout in use", "SEAT_LAYOUT_IN_USE", "Create a new auditorium layout because a future screening already uses this one."); }
    private void validateZone(String zone) { try { ZoneId.of(zone); } catch (DateTimeException exception) { throw invalid("The time zone is invalid."); } }
    private CityResponse city(City v) { return new CityResponse()
                .id(v.getId())
                .name(v.getName())
                .country(v.getCountry())
                .timeZone(v.getTimeZone())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .version(v.getVersion()); }
    private TheaterResponse theater(Theater v) { return new TheaterResponse()
                .id(v.getId())
                .cityId(v.getCity().getId())
                .name(v.getName())
                .address(v.getAddress())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .version(v.getVersion()); }
    private AuditoriumResponse auditorium(Auditorium v) { return new AuditoriumResponse()
                .id(v.getId())
                .theaterId(v.getTheater().getId())
                .name(v.getName())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .version(v.getVersion()); }
    private SeatResponse seat(Seat v) { return new SeatResponse()
                .id(v.getId())
                .auditoriumId(v.getAuditorium().getId())
                .rowLabel(v.getRowLabel())
                .seatNumber(v.getSeatNumber())
                .category(v.getCategory())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .version(v.getVersion()); }
    private MovieResponse movie(Movie v) { return new MovieResponse()
                .id(v.getId())
                .title(v.getTitle())
                .durationMinutes(v.getDurationMinutes())
                .language(v.getLanguage())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .version(v.getVersion()); }
}
