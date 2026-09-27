package com.vijaypurohit.movietickets.screening.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.screening.application.ScreeningBrowseService;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.ScreeningDetails;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.ScreeningSummary;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.SeatAvailability;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;

@Validated
@RestController
@RequestMapping(value = "/api/v1/screenings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Public screenings")
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningBrowseController {
    private final ScreeningBrowseService service;

    public ScreeningBrowseController(ScreeningBrowseService service) { this.service = service; }

    @Operation(summary = "Browse screenings with an opaque start-time cursor")
    @GetMapping
    public CursorPageResponse<ScreeningSummary> browse(@RequestParam @NotNull UUID cityId,
            @RequestParam(required = false) UUID movieId, @RequestParam(required = false) UUID theaterId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return service.browse(cityId, movieId, theaterId, date, cursor, limit);
    }
    @Operation(summary = "Get screening and price details")
    @GetMapping("/{id}") public ScreeningDetails get(@PathVariable UUID id) { return service.get(id); }
    @Operation(summary = "Get the effective seat availability for a screening")
    @GetMapping("/{id}/seats") public List<SeatAvailability> seats(@PathVariable UUID id) { return service.seats(id); }
}
