package com.vijaypurohit.movietickets.payment;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentGateway {
    ChargeResult charge(String idempotencyKey, BigDecimal amount, String paymentToken);
    Optional<ChargeResult> findChargeResult(String idempotencyKey);
    RefundResult refund(String idempotencyKey, BigDecimal amount);

    record ChargeResult(boolean succeeded, String providerReference) { }
    record RefundResult(boolean succeeded, boolean retryable, String providerReference) { }
}
