package com.vijaypurohit.movietickets.screening.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScreeningSeatTest {
    @Test
    void enforcesReservationCheckoutBookingAndCancellationTransitions() {
        UUID reservationId = UUID.randomUUID();
        ScreeningSeat seat = seat();

        seat.reserve(reservationId);
        assertThat(seat.getState()).isEqualTo(ScreeningSeatState.RESERVED);
        seat.beginCheckout(reservationId);
        assertThat(seat.getState()).isEqualTo(ScreeningSeatState.PAYMENT_IN_PROGRESS);
        seat.book(reservationId);
        assertThat(seat.getState()).isEqualTo(ScreeningSeatState.BOOKED);
        assertThat(seat.getReservationId()).isNull();
        seat.cancelBooking();
        assertThat(seat.getState()).isEqualTo(ScreeningSeatState.AVAILABLE);
    }

    @Test
    void wrongReservationCannotAdvanceOrReleaseASeat() {
        UUID owner = UUID.randomUUID();
        ScreeningSeat seat = seat();
        seat.reserve(owner);

        assertThatThrownBy(() -> seat.beginCheckout(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        seat.release(UUID.randomUUID());
        assertThat(seat.getReservationId()).isEqualTo(owner);
        assertThat(seat.getState()).isEqualTo(ScreeningSeatState.RESERVED);
    }

    private ScreeningSeat seat() {
        Instant start = Instant.parse("2026-10-10T10:00:00Z");
        Screening screening = new Screening(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(7200));
        return new ScreeningSeat(UUID.randomUUID(), screening, UUID.randomUUID());
    }
}
