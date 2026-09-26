package com.vijaypurohit.movietickets.pricing.application;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.pricing.model.DiscountCode;
import com.vijaypurohit.movietickets.pricing.model.PricingPlan;
import com.vijaypurohit.movietickets.pricing.model.RefundPolicy;
import com.vijaypurohit.movietickets.pricing.model.RefundPolicy.RuleDefinition;
import com.vijaypurohit.movietickets.pricing.persistence.DiscountCodeRepository;
import com.vijaypurohit.movietickets.pricing.persistence.PricingPlanRepository;
import com.vijaypurohit.movietickets.pricing.persistence.RefundPolicyRepository;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.DiscountCodeRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.PricingPlanRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.RefundPolicyRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.DiscountCodeResponse;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.PricingPlanResponse;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.RefundPolicyResponse;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.RefundRuleResponse;
import com.vijaypurohit.movietickets.shared.error.BadRequestException;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;
import com.vijaypurohit.movietickets.shared.pagination.PageLimits;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

@Service
@ConditionalOnProperty(prefix = "app.pricing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricingAdminService {
    private static final String INR = "INR";
    private final PricingPlanRepository pricingPlans;
    private final DiscountCodeRepository discountCodes;
    private final RefundPolicyRepository refundPolicies;
    private final IdGenerator ids;

    public PricingAdminService(PricingPlanRepository pricingPlans, DiscountCodeRepository discountCodes,
            RefundPolicyRepository refundPolicies, IdGenerator ids) {
        this.pricingPlans = pricingPlans;
        this.discountCodes = discountCodes;
        this.refundPolicies = refundPolicies;
        this.ids = ids;
    }

    @Transactional
    public PricingPlanResponse createPricingPlan(PricingPlanRequest request) {
        return response(pricingPlans.save(new PricingPlan(ids.nextId(), request.name(), request.regularPrice(),
                request.premiumPrice(), request.weekendAdjustment())));
    }
    @Transactional(readOnly = true) public PricingPlanResponse getPricingPlan(UUID id) { return response(requirePricingPlan(id)); }
    @Transactional(readOnly = true) public PageResponse<PricingPlanResponse> listPricingPlans(Integer page, Integer size) { return PageResponse.from(pricingPlans.findAll(page(page, size, "name")).map(this::response)); }
    @Transactional public PricingPlanResponse updatePricingPlan(UUID id, PricingPlanRequest request) { PricingPlan plan = requirePricingPlan(id); plan.update(request.name(), request.regularPrice(), request.premiumPrice(), request.weekendAdjustment()); return response(plan); }
    @Transactional public void deactivatePricingPlan(UUID id) { requirePricingPlan(id).deactivate(); }

    @Transactional
    public DiscountCodeResponse createDiscountCode(DiscountCodeRequest request) {
        validateDiscount(request);
        return response(discountCodes.save(new DiscountCode(ids.nextId(), request.code(), request.type(), request.value(),
                request.validFrom(), request.validUntil(), request.minimumSpend(), request.maximumDiscount(),
                request.globalUsageLimit(), request.perCustomerUsageLimit())));
    }
    @Transactional(readOnly = true) public DiscountCodeResponse getDiscountCode(UUID id) { return response(requireDiscount(id)); }
    @Transactional(readOnly = true) public PageResponse<DiscountCodeResponse> listDiscountCodes(Integer page, Integer size) { return PageResponse.from(discountCodes.findAll(page(page, size, "code")).map(this::response)); }
    @Transactional public DiscountCodeResponse updateDiscountCode(UUID id, DiscountCodeRequest request) { validateDiscount(request); DiscountCode code = requireDiscount(id); code.update(request.code(), request.type(), request.value(), request.validFrom(), request.validUntil(), request.minimumSpend(), request.maximumDiscount(), request.globalUsageLimit(), request.perCustomerUsageLimit()); return response(code); }
    @Transactional public void deactivateDiscountCode(UUID id) { requireDiscount(id).deactivate(); }

    @Transactional
    public RefundPolicyResponse createRefundPolicy(RefundPolicyRequest request) {
        RefundPolicy policy = new RefundPolicy(ids.nextId(), request.name());
        policy.replace(request.name(), definitions(request));
        return response(refundPolicies.save(policy));
    }
    @Transactional(readOnly = true) public RefundPolicyResponse getRefundPolicy(UUID id) { return response(requirePolicy(id)); }
    @Transactional(readOnly = true) public PageResponse<RefundPolicyResponse> listRefundPolicies(Integer page, Integer size) { return PageResponse.from(refundPolicies.findAll(page(page, size, "name")).map(this::response)); }
    @Transactional public RefundPolicyResponse updateRefundPolicy(UUID id, RefundPolicyRequest request) { RefundPolicy policy = requirePolicy(id); policy.replace(request.name(), definitions(request)); return response(policy); }
    @Transactional public void deactivateRefundPolicy(UUID id) { requirePolicy(id).deactivate(); }

    private List<RuleDefinition> definitions(RefundPolicyRequest request) {
        return request.rules().stream().map(rule -> new RuleDefinition(ids.nextId(), rule.cutoffMinutes(), rule.refundPercentage())).toList();
    }
    private void validateDiscount(DiscountCodeRequest request) {
        if (!request.validUntil().isAfter(request.validFrom())) throw new BadRequestException("/problems/invalid-validity", "Invalid validity window", "INVALID_VALIDITY_WINDOW", "validUntil must be after validFrom.");
        if (request.type().name().equals("PERCENTAGE") && request.value().compareTo(new java.math.BigDecimal("100")) > 0) throw new BadRequestException("/problems/invalid-discount", "Invalid discount", "INVALID_DISCOUNT", "Percentage discount cannot exceed 100.");
    }
    private PageRequest page(Integer page, Integer size, String property) { return PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size), Sort.by(property).ascending().and(Sort.by("id"))); }
    private PricingPlan requirePricingPlan(UUID id) { return pricingPlans.findById(id).orElseThrow(() -> missing("Pricing plan")); }
    private DiscountCode requireDiscount(UUID id) { return discountCodes.findById(id).orElseThrow(() -> missing("Discount code")); }
    private RefundPolicy requirePolicy(UUID id) { return refundPolicies.findById(id).orElseThrow(() -> missing("Refund policy")); }
    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException("/problems/not-found", resource + " not found", "RESOURCE_NOT_FOUND", "The requested resource was not found."); }

    private PricingPlanResponse response(PricingPlan value) { return new PricingPlanResponse(value.getId(), value.getName(), value.getRegularPrice(), value.getPremiumPrice(), value.getWeekendAdjustment(), INR, value.isActive(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion()); }
    private DiscountCodeResponse response(DiscountCode value) { return new DiscountCodeResponse(value.getId(), value.getCode(), value.getType(), value.getValue(), value.getValidFrom(), value.getValidUntil(), value.getMinimumSpend(), value.getMaximumDiscount(), value.getGlobalUsageLimit(), value.getPerCustomerUsageLimit(), INR, value.isActive(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion()); }
    private RefundPolicyResponse response(RefundPolicy value) { return new RefundPolicyResponse(value.getId(), value.getName(), value.getRules().stream().map(rule -> new RefundRuleResponse(rule.getId(), rule.getCutoffMinutes(), rule.getRefundPercentage())).toList(), value.isActive(), value.getCreatedAt(), value.getUpdatedAt(), value.getVersion()); }
}
