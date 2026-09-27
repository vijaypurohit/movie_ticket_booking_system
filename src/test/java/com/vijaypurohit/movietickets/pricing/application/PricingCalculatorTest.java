package com.vijaypurohit.movietickets.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.pricing.model.PricingPlan;

class PricingCalculatorTest {
    private final PricingCalculator calculator = new PricingCalculator();
    private final PricingPlan plan = new PricingPlan(UUID.randomUUID(), "Standard",
            new BigDecimal("199.99"), new BigDecimal("349.99"), new BigDecimal("50.01"));

    @Test
    void appliesWeekendAdjustmentUsingTheaterLocalDateAndRoundsInr() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        Instant fridayUtcButSaturdayLocal = Instant.parse("2026-10-02T19:00:00Z");

        assertThat(calculator.calculate(plan, SeatCategory.REGULAR, fridayUtcButSaturdayLocal, kolkata))
                .isEqualByComparingTo("250.00");
        assertThat(calculator.calculate(plan, SeatCategory.PREMIUM, fridayUtcButSaturdayLocal, kolkata))
                .isEqualByComparingTo("400.00");
        assertThat(calculator.calculate(new BigDecimal("199.995"), BigDecimal.ZERO,
                Instant.parse("2026-09-28T10:00:00Z"), kolkata)).isEqualByComparingTo("200.00");
    }

    @Test
    void leavesWeekdayPricesUnadjusted() {
        assertThat(calculator.calculate(plan, SeatCategory.REGULAR,
                Instant.parse("2026-09-28T10:00:00Z"), ZoneId.of("Asia/Kolkata")))
                .isEqualByComparingTo("199.99");
    }
}
