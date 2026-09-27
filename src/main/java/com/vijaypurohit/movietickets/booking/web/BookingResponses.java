package com.vijaypurohit.movietickets.booking.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vijaypurohit.movietickets.booking.model.BookingState;
import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.payment.model.PaymentStatus;
import com.vijaypurohit.movietickets.payment.model.RefundReason;
import com.vijaypurohit.movietickets.payment.model.RefundStatus;

public final class BookingResponses {
    private BookingResponses() { }

    public record BookingResponse(UUID id, String reference, UUID screeningId, BookingState state,
            List<ItemResponse> items, BigDecimal subtotal, BigDecimal discountAmount,
            BigDecimal totalAmount, String currency, PaymentStatus paymentStatus,
            List<RefundSummary> refunds, Instant createdAt) { }

    public record ItemResponse(UUID screeningSeatId, String rowLabel, int seatNumber,
            SeatCategory category, BigDecimal unitPrice) { }

    public record RefundSummary(UUID id, RefundReason reason, RefundStatus status, BigDecimal amount) { }
}
