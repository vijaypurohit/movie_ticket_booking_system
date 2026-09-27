package com.vijaypurohit.movietickets.reservation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SeatReservationTest {
    @Test void checkoutUsesItsOwnExactDeadlineAndConverts() {
        Instant originalExpiry = Instant.parse("2026-09-27T10:04:00Z");
        var reservation = new SeatReservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                originalExpiry, "key", "fingerprint");
        Instant checkoutExpiry = Instant.parse("2026-09-27T10:01:00Z");
        reservation.beginCheckout(checkoutExpiry);

        assertThat(reservation.isExpired(originalExpiry)).isFalse();
        assertThat(reservation.isCheckoutExpired(checkoutExpiry.minusNanos(1))).isFalse();
        assertThat(reservation.isCheckoutExpired(checkoutExpiry)).isTrue();
        reservation.convert();
        assertThat(reservation.getState()).isEqualTo(ReservationState.CONVERTED);
    }
}
