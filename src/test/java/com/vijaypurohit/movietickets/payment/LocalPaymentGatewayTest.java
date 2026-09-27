package com.vijaypurohit.movietickets.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LocalPaymentGatewayTest {
    @Test
    void chargeAndRefundResultsAreStableForAnIdempotencyKey() {
        LocalPaymentGateway gateway = new LocalPaymentGateway();

        var firstCharge = gateway.charge("charge-1", new BigDecimal("100.00"), "tok_success");
        var repeatedWithDifferentToken = gateway.charge("charge-1", new BigDecimal("100.00"), "tok_decline");
        var firstRefund = gateway.refund("refund-1", new BigDecimal("100.00"));
        var repeatedRefund = gateway.refund("refund-1", new BigDecimal("100.00"));

        assertThat(firstCharge).isEqualTo(repeatedWithDifferentToken);
        assertThat(firstCharge.succeeded()).isTrue();
        assertThat(gateway.findChargeResult("charge-1")).contains(firstCharge);
        assertThat(firstRefund).isEqualTo(repeatedRefund);
        assertThat(firstRefund.succeeded()).isTrue();
    }
}
