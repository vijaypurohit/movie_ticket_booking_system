package com.vijaypurohit.movietickets.pricing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vijaypurohit.movietickets.pricing.model.DiscountType;

public final class PricingResponses {
    private PricingResponses() { }

    public record PricingPlanResponse(UUID id, String name, BigDecimal regularPrice, BigDecimal premiumPrice,
            BigDecimal weekendAdjustment, String currency, boolean active, Instant createdAt, Instant updatedAt, long version) { }

    public record DiscountCodeResponse(UUID id, String code, DiscountType type, BigDecimal value,
            Instant validFrom, Instant validUntil, BigDecimal minimumSpend, BigDecimal maximumDiscount,
            Integer globalUsageLimit, Integer perCustomerUsageLimit, String currency, boolean active,
            Instant createdAt, Instant updatedAt, long version) { }

    public record RefundPolicyResponse(UUID id, String name, List<RefundRuleResponse> rules, boolean active,
            Instant createdAt, Instant updatedAt, long version) { }

    public record RefundRuleResponse(UUID id, long cutoffMinutes, BigDecimal refundPercentage) { }
}
