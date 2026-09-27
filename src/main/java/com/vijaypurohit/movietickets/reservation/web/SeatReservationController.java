package com.vijaypurohit.movietickets.reservation.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.generated.api.CustomerReservationsApi;
import com.vijaypurohit.movietickets.generated.model.CreateReservationRequest;
import com.vijaypurohit.movietickets.generated.model.ReservationResponse;
import com.vijaypurohit.movietickets.reservation.application.SeatReservationService;

@RestController
@ConditionalOnProperty(prefix = "app.reservation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeatReservationController implements CustomerReservationsApi {
    private final SeatReservationService service;

    public SeatReservationController(SeatReservationService service) { this.service = service; }

    @Override
    public ResponseEntity<ReservationResponse> createReservation(String idempotencyKey, CreateReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(idempotencyKey, request));
    }

    @Override
    public ResponseEntity<ReservationResponse> getReservation(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<Void> releaseReservation(UUID id) {
        service.release(id);
        return ResponseEntity.noContent().build();
    }
}
