package com.vijaypurohit.movietickets.screening.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.catalog.application.CatalogBrowseService;
import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.screening.model.Screening;
import com.vijaypurohit.movietickets.screening.model.ScreeningSeatState;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningSeatRepository;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.PriceSummary;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.ScreeningDetails;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.ScreeningSummary;
import com.vijaypurohit.movietickets.screening.web.ScreeningBrowseResponses.SeatAvailability;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.pagination.CursorCodec;
import com.vijaypurohit.movietickets.shared.pagination.CursorPageResponse;
import com.vijaypurohit.movietickets.shared.pagination.CursorResource;
import com.vijaypurohit.movietickets.shared.pagination.PageCursor;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;

@Service
@ConditionalOnProperty(prefix = "app.browse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningBrowseService {
    private static final UUID FIRST_ID = new UUID(0, 0);
    private final ScreeningRepository screenings;
    private final ScreeningSeatRepository seats;
    private final CatalogBrowseService catalog;
    private final CursorCodec cursors;

    public ScreeningBrowseService(ScreeningRepository screenings, ScreeningSeatRepository seats,
            CatalogBrowseService catalog, CursorCodec cursors) {
        this.screenings = screenings; this.seats = seats; this.catalog = catalog; this.cursors = cursors;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ScreeningSummary> browse(UUID cityId, UUID movieId, UUID theaterId,
            LocalDate date, String encodedCursor, Integer requestedLimit) {
        int limit = PageLimits.resolve(requestedLimit);
        var range = catalog.dayRange(cityId, date);
        PageCursor cursor = encodedCursor == null ? null : cursors.decode(encodedCursor, CursorResource.SCREENING);
        Instant cursorTime = cursor == null ? Instant.EPOCH : cursor.sortValue();
        UUID cursorId = cursor == null ? FIRST_ID : cursor.id();
        List<Screening> rows = screenings.browse(cityId, movieId, theaterId, range.start(), range.end(),
                cursorTime, cursorId, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Screening> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? cursors.encode(new PageCursor(CursorResource.SCREENING,
                        page.get(page.size() - 1).getStartTime(), page.get(page.size() - 1).getId()))
                : null;
        return new CursorPageResponse<>(page.stream().map(this::summary).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public ScreeningDetails get(UUID id) {
        Screening screening = requireActive(id);
        List<PriceSummary> prices = screening.getPrices().stream()
                .sorted(Comparator.comparing(price -> price.getSeatCategory().name()))
                .map(price -> new PriceSummary(price.getSeatCategory(), price.getAmount(), price.getCurrency())).toList();
        return new ScreeningDetails(screening.getId(), screening.getMovieId(), screening.getAuditoriumId(),
                screening.getStartTime(), screening.getEndTime(), prices, screening.getSeats().size());
    }

    @Transactional(readOnly = true)
    public List<SeatAvailability> seats(UUID screeningId) {
        requireActive(screeningId);
        return seats.findAvailability(screeningId).stream()
                .map(value -> new SeatAvailability(value.getId(), value.getSeatId(), value.getRowLabel(),
                        value.getSeatNumber(), SeatCategory.valueOf(value.getCategory()),
                        ScreeningSeatState.valueOf(value.getState())))
                .toList();
    }

    private Screening requireActive(UUID id) {
        return screenings.findDetailedById(id).filter(value -> value.getStatus() == ScreeningStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("/problems/not-found", "Screening not found", "RESOURCE_NOT_FOUND", "The requested screening was not found."));
    }
    private ScreeningSummary summary(Screening value) { return new ScreeningSummary(value.getId(), value.getMovieId(), value.getAuditoriumId(), value.getStartTime(), value.getEndTime()); }
}
