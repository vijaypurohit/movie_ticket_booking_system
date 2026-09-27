package com.vijaypurohit.movietickets.booking.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;
import com.vijaypurohit.movietickets.screening.persistence.ScreeningRepository;

/**
 * Finishes draining cancelled screenings whose booking settlement was interrupted, so an admin
 * request that fails part way through still completes without operator action.
 */
@Component
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreeningCancellationSweeper {
    private final ScreeningRepository screenings;
    private final ScreeningCancellationService cancellations;

    public ScreeningCancellationSweeper(ScreeningRepository screenings, ScreeningCancellationService cancellations) {
        this.screenings = screenings; this.cancellations = cancellations;
    }

    @Scheduled(fixedDelayString = "${app.booking.cleanup-delay:PT30S}")
    public int sweep() {
        int settled = 0;
        for (var screeningId : unsettled()) settled += cancellations.settleBookings(screeningId);
        return settled;
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.UUID> unsettled() {
        return screenings.findWithUnsettledBookings(ScreeningStatus.CANCELLED, PageRequest.of(0, 100));
    }
}
