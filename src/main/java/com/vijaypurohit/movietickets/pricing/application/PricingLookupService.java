package com.vijaypurohit.movietickets.pricing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.vijaypurohit.movietickets.pricing.persistence.PricingPlanRepository;
import com.vijaypurohit.movietickets.pricing.persistence.RefundPolicyRepository;
import com.vijaypurohit.movietickets.pricing.persistence.DiscountCodeRepository;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "app.pricing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricingLookupService {
    private final PricingPlanRepository pricingPlans;
    private final RefundPolicyRepository refundPolicies;
    private final DiscountCodeRepository discountCodes;
    private final DiscountCalculator discountCalculator;

    public PricingLookupService(PricingPlanRepository pricingPlans, RefundPolicyRepository refundPolicies,
            DiscountCodeRepository discountCodes, DiscountCalculator discountCalculator) {
        this.pricingPlans = pricingPlans;
        this.refundPolicies = refundPolicies;
        this.discountCodes = discountCodes;
        this.discountCalculator = discountCalculator;
    }

    public PricingSnapshot requireActivePricingPlan(UUID id) {
        var plan = pricingPlans.findById(id).orElseThrow(() -> missing("Pricing plan"));
        if (!plan.isActive()) throw inactive("pricing plan");
        return new PricingSnapshot(plan.getId(), plan.getRegularPrice(), plan.getPremiumPrice(), plan.getWeekendAdjustment());
    }

    public void requireActiveRefundPolicy(UUID id) {
        var policy = refundPolicies.findById(id).orElseThrow(() -> missing("Refund policy"));
        if (!policy.isActive()) throw inactive("refund policy");
    }

    public DiscountQuote quoteDiscount(String code, BigDecimal subtotal) {
        var discount = discountCodes.findByCodeForUpdate(code.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> missing("Discount code"));
        var result = discountCalculator.calculate(discount, subtotal);
        return new DiscountQuote(discount.getId(), result.discount(), result.total(),
                discount.getGlobalUsageLimit(), discount.getPerCustomerUsageLimit());
    }

    public List<RefundRuleSnapshot> refundRules(UUID policyId) {
        var policy = refundPolicies.findById(policyId).orElseThrow(() -> missing("Refund policy"));
        if (!policy.isActive()) throw inactive("refund policy");
        return policy.getRules().stream()
                .map(rule -> new RefundRuleSnapshot(rule.getCutoffMinutes(), rule.getRefundPercentage()))
                .toList();
    }

    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException("not-found", resource + " not found", "RESOURCE_NOT_FOUND", "The requested resource was not found."); }
    private BusinessRuleViolationException inactive(String resource) { return new BusinessRuleViolationException("inactive-configuration", "Inactive configuration", "INACTIVE_CONFIGURATION", "The selected " + resource + " is inactive."); }

    public record PricingSnapshot(UUID id, BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal weekendAdjustment) { }
    public record DiscountQuote(UUID discountCodeId, BigDecimal discount, BigDecimal total,
            Integer globalLimit, Integer perCustomerLimit) { }
    public record RefundRuleSnapshot(long cutoffMinutes, BigDecimal percentage) { }
}
