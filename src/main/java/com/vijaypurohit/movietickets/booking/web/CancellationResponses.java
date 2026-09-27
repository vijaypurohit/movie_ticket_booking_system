package com.vijaypurohit.movietickets.booking.web;

import java.util.UUID;

import com.vijaypurohit.movietickets.booking.model.BookingState;
import com.vijaypurohit.movietickets.payment.web.RefundResponses.RefundResponse;

public final class CancellationResponses {
    private CancellationResponses() { }
    public record CancellationResponse(UUID bookingId, BookingState bookingState, RefundResponse refund) { }
}
