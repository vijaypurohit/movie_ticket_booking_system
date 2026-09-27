package com.vijaypurohit.movietickets.payment.web;

import java.math.BigDecimal;
import java.util.UUID;

import com.vijaypurohit.movietickets.payment.model.RefundReason;
import com.vijaypurohit.movietickets.payment.model.RefundStatus;

public final class RefundResponses {
    private RefundResponses() { }
    public record RefundResponse(UUID id, UUID bookingId, RefundReason reason, RefundStatus status,
            BigDecimal amount, String currency) { }
}
