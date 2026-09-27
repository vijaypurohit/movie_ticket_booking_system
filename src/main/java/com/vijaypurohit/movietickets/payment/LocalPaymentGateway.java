package com.vijaypurohit.movietickets.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalPaymentGateway implements PaymentGateway {
    private final ConcurrentHashMap<String, ChargeResult> outcomes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RefundResult> refundOutcomes = new ConcurrentHashMap<>();

    @Override
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String paymentToken) {
        return outcomes.computeIfAbsent(idempotencyKey, ignored -> {
            boolean success = "tok_success".equals(paymentToken);
            String prefix = success ? "pay_local_" : "decline_local_";
            String reference = prefix + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return new ChargeResult(success, reference);
        });
    }

    @Override
    public Optional<ChargeResult> findChargeResult(String idempotencyKey) {
        return Optional.ofNullable(outcomes.get(idempotencyKey));
    }

    @Override
    public RefundResult refund(String idempotencyKey, BigDecimal amount) {
        return refundOutcomes.computeIfAbsent(idempotencyKey, ignored -> {
            boolean fails = idempotencyKey.startsWith("force-fail:");
            String prefix = fails ? "refund_failed_" : "refund_local_";
            String reference = prefix + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return new RefundResult(!fails, false, reference);
        });
    }
}
