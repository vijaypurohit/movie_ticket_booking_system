package com.vijaypurohit.movietickets.pricing.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.pricing.application.PricingAdminService;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.DiscountCodeRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.PricingPlanRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingRequests.RefundPolicyRequest;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.DiscountCodeResponse;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.PricingPlanResponse;
import com.vijaypurohit.movietickets.pricing.web.PricingResponses.RefundPolicyResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;
import com.vijaypurohit.movietickets.shared.pagination.PageResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/admin/api/v1")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.pricing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricingAdminController {
    private final PricingAdminService service;

    public PricingAdminController(PricingAdminService service) { this.service = service; }

    @PostMapping("/pricing-plans") @ResponseStatus(HttpStatus.CREATED)
    public PricingPlanResponse createPricingPlan(@Valid @RequestBody PricingPlanRequest request) { return service.createPricingPlan(request); }
    @GetMapping("/pricing-plans/{id}") public PricingPlanResponse getPricingPlan(@PathVariable UUID id) { return service.getPricingPlan(id); }
    @GetMapping("/pricing-plans") public PageResponse<PricingPlanResponse> listPricingPlans(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listPricingPlans(page, size); }
    @PutMapping("/pricing-plans/{id}") public PricingPlanResponse updatePricingPlan(@PathVariable UUID id, @Valid @RequestBody PricingPlanRequest request) { return service.updatePricingPlan(id, request); }
    @DeleteMapping("/pricing-plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivatePricingPlan(@PathVariable UUID id) { service.deactivatePricingPlan(id); }

    @PostMapping("/discount-codes") @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse createDiscountCode(@Valid @RequestBody DiscountCodeRequest request) { return service.createDiscountCode(request); }
    @GetMapping("/discount-codes/{id}") public DiscountCodeResponse getDiscountCode(@PathVariable UUID id) { return service.getDiscountCode(id); }
    @GetMapping("/discount-codes") public PageResponse<DiscountCodeResponse> listDiscountCodes(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listDiscountCodes(page, size); }
    @PutMapping("/discount-codes/{id}") public DiscountCodeResponse updateDiscountCode(@PathVariable UUID id, @Valid @RequestBody DiscountCodeRequest request) { return service.updateDiscountCode(id, request); }
    @DeleteMapping("/discount-codes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateDiscountCode(@PathVariable UUID id) { service.deactivateDiscountCode(id); }

    @PostMapping("/refund-policies") @ResponseStatus(HttpStatus.CREATED)
    public RefundPolicyResponse createRefundPolicy(@Valid @RequestBody RefundPolicyRequest request) { return service.createRefundPolicy(request); }
    @GetMapping("/refund-policies/{id}") public RefundPolicyResponse getRefundPolicy(@PathVariable UUID id) { return service.getRefundPolicy(id); }
    @GetMapping("/refund-policies") public PageResponse<RefundPolicyResponse> listRefundPolicies(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) { return service.listRefundPolicies(page, size); }
    @PutMapping("/refund-policies/{id}") public RefundPolicyResponse updateRefundPolicy(@PathVariable UUID id, @Valid @RequestBody RefundPolicyRequest request) { return service.updateRefundPolicy(id, request); }
    @DeleteMapping("/refund-policies/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateRefundPolicy(@PathVariable UUID id) { service.deactivateRefundPolicy(id); }
}
