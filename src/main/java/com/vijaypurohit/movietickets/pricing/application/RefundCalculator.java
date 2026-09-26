package com.vijaypurohit.movietickets.pricing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.pricing.model.RefundPolicyRule;

@Component
public class RefundCalculator {
    private final Clock clock;

    public RefundCalculator(Clock clock) { this.clock = clock; }

    public BigDecimal calculate(List<RefundPolicyRule> rules, Instant screeningStart, BigDecimal capturedAmount) {
        Objects.requireNonNull(rules);
        Objects.requireNonNull(screeningStart);
        Objects.requireNonNull(capturedAmount);
        long minutesRemaining = Duration.between(clock.instant(), screeningStart).toMinutes();
        BigDecimal percentage = rules.stream()
                .filter(rule -> minutesRemaining >= rule.getCutoffMinutes())
                .max(Comparator.comparingLong(RefundPolicyRule::getCutoffMinutes))
                .map(RefundPolicyRule::getRefundPercentage)
                .orElse(BigDecimal.ZERO);
        return capturedAmount.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                .min(capturedAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
