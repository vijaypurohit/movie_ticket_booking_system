package com.vijaypurohit.movietickets.booking.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BookingRequests {
    private BookingRequests() { }

    public record CreateBookingRequest(
            @NotNull UUID reservationId,
            @Size(max = 40) String discountCode,
            @NotBlank @Size(max = 100) String paymentToken) { }
}
