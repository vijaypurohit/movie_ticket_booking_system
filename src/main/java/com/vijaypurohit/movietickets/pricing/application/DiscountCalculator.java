package com.vijaypurohit.movietickets.pricing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.pricing.model.DiscountCode;
import com.vijaypurohit.movietickets.pricing.model.DiscountType;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;

@Component
public class DiscountCalculator {
    private final Clock clock;

    public DiscountCalculator(Clock clock) { this.clock = clock; }

    public DiscountResult calculate(DiscountCode code, BigDecimal subtotal) {
        Objects.requireNonNull(code);
        Objects.requireNonNull(subtotal);
        Instant now = clock.instant();
        if (!code.isActive() || now.isBefore(code.getValidFrom()) || !now.isBefore(code.getValidUntil())) {
            throw new BusinessRuleViolationException("discount-not-valid", "Discount not valid", "DISCOUNT_NOT_VALID", "The discount code is not currently valid.");
        }
        if (subtotal.compareTo(code.getMinimumSpend()) < 0) {
            throw new BusinessRuleViolationException("minimum-spend", "Minimum spend not met", "MINIMUM_SPEND_NOT_MET", "The order does not meet the discount minimum spend.");
        }
        BigDecimal discount = code.getType() == DiscountType.FIXED
                ? code.getValue()
                : subtotal.multiply(code.getValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (code.getMaximumDiscount() != null) discount = discount.min(code.getMaximumDiscount());
        discount = discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
        return new DiscountResult(discount, subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP));
    }

    public record DiscountResult(BigDecimal discount, BigDecimal total) { }
}
