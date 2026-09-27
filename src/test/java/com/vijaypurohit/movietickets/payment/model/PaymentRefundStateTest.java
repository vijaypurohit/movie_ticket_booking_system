package com.vijaypurohit.movietickets.payment.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PaymentRefundStateTest {
    @Test
    void paymentFinalizationIsIdempotentAndDeclineCannotBecomeSuccess() {
        Payment payment = new Payment(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("500.00"), "charge-key");
        payment.decline("declined");
        payment.succeed("unexpected-success");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    void refundTracksBoundedProcessingAttemptsAndTerminalState() {
        Refund refund = new Refund(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                RefundReason.CANCELLATION, new BigDecimal("500.00"), "refund-key", Instant.EPOCH);

        refund.beginProcessing(Instant.EPOCH.plusSeconds(60));
        assertThat(refund.getAttemptCount()).isEqualTo(1);
        refund.retryAt(Instant.EPOCH.plusSeconds(2));
        refund.beginProcessing(Instant.EPOCH.plusSeconds(62));
        refund.succeed("refund-reference");

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThatThrownBy(() -> refund.beginProcessing(Instant.EPOCH.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }
}
