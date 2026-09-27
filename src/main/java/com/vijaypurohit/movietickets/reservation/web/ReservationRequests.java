package com.vijaypurohit.movietickets.reservation.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReservationRequests {
    private ReservationRequests() { }
    public record CreateReservationRequest(@NotNull @Size(min = 1, max = 10) List<@NotNull UUID> screeningSeatIds) { }
}
