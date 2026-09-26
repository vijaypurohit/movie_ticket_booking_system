package com.vijaypurohit.movietickets.pricing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.pricing.model.PricingPlan;

@Component
public class PricingCalculator {
    public BigDecimal calculate(PricingPlan plan, SeatCategory category, Instant screeningStart, ZoneId theaterZone) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(category);
        DayOfWeek day = Objects.requireNonNull(screeningStart).atZone(Objects.requireNonNull(theaterZone)).getDayOfWeek();
        BigDecimal base = category == SeatCategory.PREMIUM ? plan.getPremiumPrice() : plan.getRegularPrice();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) base = base.add(plan.getWeekendAdjustment());
        return base.setScale(2, RoundingMode.HALF_UP);
    }
}
