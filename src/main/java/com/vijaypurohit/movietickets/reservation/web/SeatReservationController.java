package com.vijaypurohit.movietickets.reservation.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.reservation.application.SeatReservationService;
import com.vijaypurohit.movietickets.reservation.web.ReservationRequests.CreateReservationRequest;
import com.vijaypurohit.movietickets.reservation.web.ReservationResponses.ReservationResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping(value = "/api/v1/seat-reservations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer reservations")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.reservation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeatReservationController {
    private final SeatReservationService service;

    public SeatReservationController(SeatReservationService service) { this.service = service; }

    @Operation(summary = "Reserve one to ten screening seats atomically")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @Parameter(ref = "#/components/parameters/IdempotencyKey")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody CreateReservationRequest request) {
        return service.create(idempotencyKey, request);
    }
    @Operation(summary = "Get an owned seat reservation")
    @GetMapping("/{id}") public ReservationResponse get(@PathVariable UUID id) { return service.get(id); }
    @Operation(summary = "Release an owned seat reservation idempotently")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void release(@PathVariable UUID id) { service.release(id); }
}
