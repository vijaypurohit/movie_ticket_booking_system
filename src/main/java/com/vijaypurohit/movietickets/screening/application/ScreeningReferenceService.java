package com.vijaypurohit.movietickets.screening.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.vijaypurohit.movietickets.catalog.application.FutureScreeningChecker;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;

@Service
@ConditionalOnProperty(prefix = "app.screening", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningReferenceService implements FutureScreeningChecker {
    private final ScreeningRepository screenings;

    public ScreeningReferenceService(ScreeningRepository screenings) { this.screenings = screenings; }

    @Override
    public boolean hasFutureScreening(UUID auditoriumId, Instant after) {
        return screenings.existsByAuditoriumIdAndStatusAndStartTimeAfter(auditoriumId, ScreeningStatus.ACTIVE, after);
    }
}
