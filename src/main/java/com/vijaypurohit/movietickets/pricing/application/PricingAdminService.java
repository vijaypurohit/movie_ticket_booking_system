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
import com.vijaypurohit.movietickets.generated.model.DiscountCodeRequest;
import com.vijaypurohit.movietickets.generated.model.PricingPlanRequest;
import com.vijaypurohit.movietickets.generated.model.RefundPolicyRequest;
import com.vijaypurohit.movietickets.generated.model.DiscountCodeResponse;
import com.vijaypurohit.movietickets.generated.model.PricingPlanResponse;
import com.vijaypurohit.movietickets.generated.model.RefundPolicyResponse;
import com.vijaypurohit.movietickets.generated.model.RefundRuleResponse;
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
        return response(pricingPlans.save(new PricingPlan(ids.nextId(), request.getName(), request.getRegularPrice(),
                request.getPremiumPrice(), request.getWeekendAdjustment())));
    }
    @Transactional(readOnly = true) public PricingPlanResponse getPricingPlan(UUID id) { return response(requirePricingPlan(id)); }
    @Transactional(readOnly = true) public PageResponse<PricingPlanResponse> listPricingPlans(Integer page, Integer size) { return PageResponse.from(pricingPlans.findAll(page(page, size, "name")).map(this::response)); }
    @Transactional public PricingPlanResponse updatePricingPlan(UUID id, PricingPlanRequest request) { PricingPlan plan = requirePricingPlan(id); plan.update(request.getName(), request.getRegularPrice(), request.getPremiumPrice(), request.getWeekendAdjustment()); return response(plan); }
    @Transactional public void deactivatePricingPlan(UUID id) { requirePricingPlan(id).deactivate(); }

    @Transactional
    public DiscountCodeResponse createDiscountCode(DiscountCodeRequest request) {
        validateDiscount(request);
        return response(discountCodes.save(new DiscountCode(ids.nextId(), request.getCode(), request.getType(), request.getValue(),
                request.getValidFrom(), request.getValidUntil(), request.getMinimumSpend(), request.getMaximumDiscount(),
                request.getGlobalUsageLimit(), request.getPerCustomerUsageLimit())));
    }
    @Transactional(readOnly = true) public DiscountCodeResponse getDiscountCode(UUID id) { return response(requireDiscount(id)); }
    @Transactional(readOnly = true) public PageResponse<DiscountCodeResponse> listDiscountCodes(Integer page, Integer size) { return PageResponse.from(discountCodes.findAll(page(page, size, "code")).map(this::response)); }
    @Transactional public DiscountCodeResponse updateDiscountCode(UUID id, DiscountCodeRequest request) { validateDiscount(request); DiscountCode code = requireDiscount(id); code.update(request.getCode(), request.getType(), request.getValue(), request.getValidFrom(), request.getValidUntil(), request.getMinimumSpend(), request.getMaximumDiscount(), request.getGlobalUsageLimit(), request.getPerCustomerUsageLimit()); return response(code); }
    @Transactional public void deactivateDiscountCode(UUID id) { requireDiscount(id).deactivate(); }

    @Transactional
    public RefundPolicyResponse createRefundPolicy(RefundPolicyRequest request) {
        RefundPolicy policy = new RefundPolicy(ids.nextId(), request.getName());
        policy.replace(request.getName(), definitions(request));
        return response(refundPolicies.save(policy));
    }
    @Transactional(readOnly = true) public RefundPolicyResponse getRefundPolicy(UUID id) { return response(requirePolicy(id)); }
    @Transactional(readOnly = true) public PageResponse<RefundPolicyResponse> listRefundPolicies(Integer page, Integer size) { return PageResponse.from(refundPolicies.findAll(page(page, size, "name")).map(this::response)); }
    @Transactional public RefundPolicyResponse updateRefundPolicy(UUID id, RefundPolicyRequest request) { RefundPolicy policy = requirePolicy(id); policy.replace(request.getName(), definitions(request)); return response(policy); }
    @Transactional public void deactivateRefundPolicy(UUID id) { requirePolicy(id).deactivate(); }

    private List<RuleDefinition> definitions(RefundPolicyRequest request) {
        return request.getRules().stream().map(rule -> new RuleDefinition(ids.nextId(), rule.getCutoffMinutes(), rule.getRefundPercentage())).toList();
    }
    private void validateDiscount(DiscountCodeRequest request) {
        if (!request.getValidUntil().isAfter(request.getValidFrom())) throw new BadRequestException("invalid-validity", "Invalid validity window", "INVALID_VALIDITY_WINDOW", "validUntil must be after validFrom.");
        if (request.getType().name().equals("PERCENTAGE") && request.getValue().compareTo(new java.math.BigDecimal("100")) > 0) throw new BadRequestException("invalid-discount", "Invalid discount", "INVALID_DISCOUNT", "Percentage discount cannot exceed 100.");
    }
    private PageRequest page(Integer page, Integer size, String property) { return PageRequest.of(PageLimits.resolvePage(page), PageLimits.resolve(size), Sort.by(property).ascending().and(Sort.by("id"))); }
    private PricingPlan requirePricingPlan(UUID id) { return pricingPlans.findById(id).orElseThrow(() -> missing("Pricing plan")); }
    private DiscountCode requireDiscount(UUID id) { return discountCodes.findById(id).orElseThrow(() -> missing("Discount code")); }
    private RefundPolicy requirePolicy(UUID id) { return refundPolicies.findById(id).orElseThrow(() -> missing("Refund policy")); }
    private ResourceNotFoundException missing(String resource) { return new ResourceNotFoundException("not-found", resource + " not found", "RESOURCE_NOT_FOUND", "The requested resource was not found."); }

    private PricingPlanResponse response(PricingPlan value) { return new PricingPlanResponse()
                .id(value.getId())
                .name(value.getName())
                .regularPrice(value.getRegularPrice())
                .premiumPrice(value.getPremiumPrice())
                .weekendAdjustment(value.getWeekendAdjustment())
                .currency(INR)
                .active(value.isActive())
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .version(value.getVersion()); }
    private DiscountCodeResponse response(DiscountCode value) { return new DiscountCodeResponse()
                .id(value.getId())
                .code(value.getCode())
                .type(value.getType())
                .value(value.getValue())
                .validFrom(value.getValidFrom())
                .validUntil(value.getValidUntil())
                .minimumSpend(value.getMinimumSpend())
                .maximumDiscount(value.getMaximumDiscount())
                .globalUsageLimit(value.getGlobalUsageLimit())
                .perCustomerUsageLimit(value.getPerCustomerUsageLimit())
                .currency(INR)
                .active(value.isActive())
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .version(value.getVersion()); }
    private RefundPolicyResponse response(RefundPolicy value) { return new RefundPolicyResponse()
                .id(value.getId())
                .name(value.getName())
                .rules(value.getRules().stream().map(rule -> new RefundRuleResponse()
                .id(rule.getId())
                .cutoffMinutes(rule.getCutoffMinutes())
                .refundPercentage(rule.getRefundPercentage())).toList())
                .active(value.isActive())
                .createdAt(value.getCreatedAt())
                .updatedAt(value.getUpdatedAt())
                .version(value.getVersion()); }
}
