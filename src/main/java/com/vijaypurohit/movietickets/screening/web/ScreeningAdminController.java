package com.vijaypurohit.movietickets.screening.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.screening.application.ScreeningAdminService;
import com.vijaypurohit.movietickets.screening.web.ScreeningRequests.CreateScreeningRequest;
import com.vijaypurohit.movietickets.screening.web.ScreeningResponses.ScreeningPriceResponse;
import com.vijaypurohit.movietickets.screening.web.ScreeningResponses.ScreeningResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/admin/api/v1/screenings")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.screening", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningAdminController {
    private final ScreeningAdminService service;

    public ScreeningAdminController(ScreeningAdminService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ScreeningResponse create(@Valid @RequestBody CreateScreeningRequest request) { return service.create(request); }
    @GetMapping("/{id}") public ScreeningResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping("/{id}/prices") public List<ScreeningPriceResponse> prices(@PathVariable UUID id) { return service.get(id).prices(); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@PathVariable UUID id) { service.cancel(id); }
}
