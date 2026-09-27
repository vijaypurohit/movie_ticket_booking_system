package com.vijaypurohit.movietickets.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vijaypurohit.movietickets.pricing.model.DiscountCode;
import com.vijaypurohit.movietickets.pricing.model.DiscountType;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;

class DiscountCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private final DiscountCalculator calculator = new DiscountCalculator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test void capsPercentageDiscountAndPreservesInrScale() {
        DiscountCode code = code(DiscountType.PERCENTAGE, "25.00", "50.00", "100.00");
        var result = calculator.calculate(code, new BigDecimal("400.00"));
        assertThat(result.discount()).isEqualByComparingTo("50.00");
        assertThat(result.total()).isEqualByComparingTo("350.00");
        assertThat(result.total().scale()).isEqualTo(2);
    }

    @Test void fixedDiscountCannotExceedSubtotalAndMinimumSpendIsEnforced() {
        DiscountCode code = code(DiscountType.FIXED, "500.00", null, "100.00");
        assertThat(calculator.calculate(code, new BigDecimal("200.00")).total()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> calculator.calculate(code, new BigDecimal("99.99")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test void validityIncludesStartAndExcludesEnd() {
        DiscountCode atStart = new DiscountCode(UUID.randomUUID(), "START", DiscountType.FIXED,
                new BigDecimal("10.00"), NOW, NOW.plusSeconds(60), BigDecimal.ZERO, null, null, null);
        DiscountCode atEnd = new DiscountCode(UUID.randomUUID(), "END", DiscountType.FIXED,
                new BigDecimal("10.00"), NOW.minusSeconds(60), NOW, BigDecimal.ZERO, null, null, null);

        assertThat(calculator.calculate(atStart, new BigDecimal("100.00")).discount())
                .isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> calculator.calculate(atEnd, new BigDecimal("100.00")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private DiscountCode code(DiscountType type, String value, String cap, String minimum) {
        return new DiscountCode(UUID.randomUUID(), "SAVE", type, new BigDecimal(value), NOW.minusSeconds(60),
                NOW.plusSeconds(60), new BigDecimal(minimum), cap == null ? null : new BigDecimal(cap), null, null);
    }
}
