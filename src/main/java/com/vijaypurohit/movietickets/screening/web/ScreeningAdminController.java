package com.vijaypurohit.movietickets.screening.web;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.generated.api.AdminScreeningsApi;
import com.vijaypurohit.movietickets.generated.model.CreateScreeningRequest;
import com.vijaypurohit.movietickets.generated.model.PageResponseScreeningResponse;
import com.vijaypurohit.movietickets.generated.model.ScreeningPriceResponse;
import com.vijaypurohit.movietickets.generated.model.ScreeningResponse;
import com.vijaypurohit.movietickets.screening.application.ScreeningAdminService;

@RestController
@ConditionalOnProperty(prefix = "app.screening", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningAdminController implements AdminScreeningsApi {
    private final ScreeningAdminService service;

    public ScreeningAdminController(ScreeningAdminService service) { this.service = service; }

    @Override
    public ResponseEntity<ScreeningResponse> createScreening(CreateScreeningRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Override
    public ResponseEntity<ScreeningResponse> getScreening(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<PageResponseScreeningResponse> listScreenings(
            UUID auditoriumId, UUID movieId, Integer page, Integer size) {
        var result = service.list(auditoriumId, movieId, page, size);
        return ResponseEntity.ok(new PageResponseScreeningResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<List<ScreeningPriceResponse>> listScreeningPrices(UUID id) {
        return ResponseEntity.ok(service.get(id).getPrices());
    }

    @Override
    public ResponseEntity<Void> cancelScreening(UUID id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
