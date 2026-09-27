package com.vijaypurohit.movietickets.reservation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reservation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryWorker {
    private final SeatReservationService reservations;

    public ReservationExpiryWorker(SeatReservationService reservations) { this.reservations = reservations; }

    @Scheduled(fixedDelayString = "${app.booking.cleanup-delay:PT30S}")
    public void releaseExpiredReservations() { reservations.releaseExpiredBatch(); }
}
