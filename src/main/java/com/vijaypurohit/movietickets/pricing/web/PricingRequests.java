package com.vijaypurohit.movietickets.pricing.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.vijaypurohit.movietickets.pricing.model.DiscountType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PricingRequests {
    private PricingRequests() { }

    public record PricingPlanRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal regularPrice,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal premiumPrice,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal weekendAdjustment) { }

    public record DiscountCodeRequest(
            @NotBlank @Size(max = 40) String code,
            @NotNull DiscountType type,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal value,
            @NotNull Instant validFrom,
            @NotNull Instant validUntil,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal minimumSpend,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal maximumDiscount,
            @Min(1) Integer globalUsageLimit,
            @Min(1) Integer perCustomerUsageLimit) { }

    public record RefundPolicyRequest(
            @NotBlank @Size(max = 120) String name,
            @NotEmpty @Size(max = 20) List<@Valid RefundRuleRequest> rules) { }

    public record RefundRuleRequest(
            @Min(0) long cutoffMinutes,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
            BigDecimal refundPercentage) { }
}
