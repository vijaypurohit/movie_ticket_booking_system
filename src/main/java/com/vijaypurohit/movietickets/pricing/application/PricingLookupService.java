package com.vijaypurohit.movietickets.pricing.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.vijaypurohit.movietickets.pricing.persistence.PricingPlanRepository;
import com.vijaypurohit.movietickets.pricing.persistence.RefundPolicyRepository;
import com.vijaypurohit.movietickets.shared.error.BusinessRuleViolationException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;

@Service
@ConditionalOnProperty(prefix = "app.pricing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricingLookupService {
    private final PricingPlanRepository pricingPlans;
    private final RefundPolicyRepository refundPolicies;

    public PricingLookupService(PricingPlanRepository pricingPlans, RefundPolicyRepository refundPolicies) {
        this.pricingPlans = pricingPlans;
        this.refundPolicies = refundPolicies;
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

    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException("not-found", resource + " not found", "RESOURCE_NOT_FOUND", "The requested resource was not found."); }
    private BusinessRuleViolationException inactive(String resource) { return new BusinessRuleViolationException("inactive-configuration", "Inactive configuration", "INACTIVE_CONFIGURATION", "The selected " + resource + " is inactive."); }

    public record PricingSnapshot(UUID id, BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal weekendAdjustment) { }
}
