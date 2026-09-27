package com.vijaypurohit.movietickets.booking.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.booking.application.BookingCancellationService;
import com.vijaypurohit.movietickets.booking.application.BookingService;
import com.vijaypurohit.movietickets.booking.web.BookingRequests.CreateBookingRequest;
import com.vijaypurohit.movietickets.booking.web.BookingResponses.BookingResponse;
import com.vijaypurohit.movietickets.booking.web.CancellationResponses.CancellationResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping(value = "/api/v1/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer bookings")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingController {
    private final BookingService service;
    private final BookingCancellationService cancellations;

    public BookingController(BookingService service, BookingCancellationService cancellations) {
        this.service = service; this.cancellations = cancellations;
    }

    @Operation(summary = "Pay for a reservation and create a booking")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(
            @Parameter(ref = "#/components/parameters/IdempotencyKey")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        return service.create(idempotencyKey, request);
    }

    @Operation(summary = "Get an owned booking")
    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id) { return service.get(id); }

    @Operation(summary = "List owned booking history using an opaque cursor")
    @GetMapping
    public CursorPageResponse<BookingResponse> history(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return service.history(cursor, limit);
    }

    @Operation(summary = "Cancel an owned booking idempotently")
    @PostMapping("/{id}/cancellations")
    public CancellationResponse cancel(
            @PathVariable UUID id,
            @Parameter(ref = "#/components/parameters/IdempotencyKey")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 120) String idempotencyKey) {
        return cancellations.cancel(id, idempotencyKey);
    }
}
