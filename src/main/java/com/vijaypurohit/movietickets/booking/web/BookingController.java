package com.vijaypurohit.movietickets.booking.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.booking.application.BookingCancellationService;
import com.vijaypurohit.movietickets.booking.application.BookingService;
import com.vijaypurohit.movietickets.generated.api.CustomerBookingsApi;
import com.vijaypurohit.movietickets.generated.model.BookingResponse;
import com.vijaypurohit.movietickets.generated.model.CancellationResponse;
import com.vijaypurohit.movietickets.generated.model.CreateBookingRequest;
import com.vijaypurohit.movietickets.generated.model.CursorPageResponseBookingResponse;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;

@RestController
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingController implements CustomerBookingsApi {
    private final BookingService service;
    private final BookingCancellationService cancellations;

    public BookingController(BookingService service, BookingCancellationService cancellations) {
        this.service = service; this.cancellations = cancellations;
    }

    @Override
    public ResponseEntity<BookingResponse> createBooking(String idempotencyKey, CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(idempotencyKey, request));
    }

    @Override
    public ResponseEntity<BookingResponse> getBooking(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<CursorPageResponseBookingResponse> listBookings(String cursor, Integer limit) {
        CursorPageResponse<BookingResponse> page = service.history(cursor, limit);
        return ResponseEntity.ok(new CursorPageResponseBookingResponse()
                .items(page.items())
                .nextCursor(page.nextCursor()));
    }

    @Override
    public ResponseEntity<CancellationResponse> cancelBooking(UUID id, String idempotencyKey) {
        return ResponseEntity.ok(cancellations.cancel(id, idempotencyKey));
    }
}
