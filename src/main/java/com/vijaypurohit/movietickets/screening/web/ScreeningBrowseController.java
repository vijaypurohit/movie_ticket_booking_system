package com.vijaypurohit.movietickets.screening.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.generated.api.PublicScreeningsApi;
import com.vijaypurohit.movietickets.generated.model.CursorPageResponseScreeningSummary;
import com.vijaypurohit.movietickets.generated.model.ScreeningDetails;
import com.vijaypurohit.movietickets.generated.model.ScreeningSummary;
import com.vijaypurohit.movietickets.generated.model.SeatAvailability;
import com.vijaypurohit.movietickets.screening.application.ScreeningBrowseService;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;

@RestController
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningBrowseController implements PublicScreeningsApi {
    private final ScreeningBrowseService service;

    public ScreeningBrowseController(ScreeningBrowseService service) { this.service = service; }

    @Override
    public ResponseEntity<CursorPageResponseScreeningSummary> browseScreenings(
            UUID cityId, LocalDate date, UUID movieId, UUID theaterId, String cursor, Integer limit) {
        CursorPageResponse<ScreeningSummary> result =
                service.browse(cityId, movieId, theaterId, date, cursor, limit);
        return ResponseEntity.ok(new CursorPageResponseScreeningSummary()
                .items(result.items())
                .nextCursor(result.nextCursor()));
    }

    @Override
    public ResponseEntity<ScreeningDetails> browseScreening(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<List<SeatAvailability>> browseScreeningSeats(UUID id) {
        return ResponseEntity.ok(service.seats(id));
    }
}
