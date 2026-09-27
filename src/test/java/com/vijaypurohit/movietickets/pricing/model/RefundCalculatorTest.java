package com.vijaypurohit.movietickets.pricing.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vijaypurohit.movietickets.pricing.application.RefundCalculator;

class RefundCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private final RefundPolicy policy = policy();
    private final RefundCalculator calculator = new RefundCalculator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void selectsEveryCutoffAtItsExactBoundary() {
        assertThat(refundAt(1440)).isEqualByComparingTo("800.00");
        assertThat(refundAt(120)).isEqualByComparingTo("400.00");
        assertThat(refundAt(0)).isEqualByComparingTo("0.00");
    }

    @Test
    void dropsToTheNextRuleOneInstantBeforeBoundaryAndNeverExceedsCapture() {
        Instant justBelowFullRefund = NOW.plusSeconds(1440L * 60).minusNanos(1);
        assertThat(calculator.calculate(policy.getRules(), justBelowFullRefund, new BigDecimal("800.00")))
                .isEqualByComparingTo("400.00");
    }

    private BigDecimal refundAt(long minutes) {
        return calculator.calculate(policy.getRules(), NOW.plusSeconds(minutes * 60), new BigDecimal("800.00"));
    }

    private RefundPolicy policy() {
        RefundPolicy value = new RefundPolicy(UUID.randomUUID(), "Refund policy");
        value.replace(value.getName(), List.of(
                new RefundPolicy.RuleDefinition(UUID.randomUUID(), 1440, new BigDecimal("100.00")),
                new RefundPolicy.RuleDefinition(UUID.randomUUID(), 120, new BigDecimal("50.00")),
                new RefundPolicy.RuleDefinition(UUID.randomUUID(), 0, new BigDecimal("0.00"))));
        return value;
    }
}
