package com.vijaypurohit.movietickets.pricing.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.generated.api.AdminPricingApi;
import com.vijaypurohit.movietickets.generated.model.DiscountCodeRequest;
import com.vijaypurohit.movietickets.generated.model.DiscountCodeResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseDiscountCodeResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponsePricingPlanResponse;
import com.vijaypurohit.movietickets.generated.model.PageResponseRefundPolicyResponse;
import com.vijaypurohit.movietickets.generated.model.PricingPlanRequest;
import com.vijaypurohit.movietickets.generated.model.PricingPlanResponse;
import com.vijaypurohit.movietickets.generated.model.RefundPolicyRequest;
import com.vijaypurohit.movietickets.generated.model.RefundPolicyResponse;
import com.vijaypurohit.movietickets.pricing.application.PricingAdminService;

@RestController
@ConditionalOnProperty(prefix = "app.pricing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PricingAdminController implements AdminPricingApi {
    private final PricingAdminService service;

    public PricingAdminController(PricingAdminService service) { this.service = service; }

    @Override
    public ResponseEntity<PricingPlanResponse> createPricingPlan(PricingPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPricingPlan(request));
    }

    @Override
    public ResponseEntity<PricingPlanResponse> getPricingPlan(UUID id) {
        return ResponseEntity.ok(service.getPricingPlan(id));
    }

    @Override
    public ResponseEntity<PageResponsePricingPlanResponse> listPricingPlans(Integer page, Integer size) {
        var result = service.listPricingPlans(page, size);
        return ResponseEntity.ok(new PageResponsePricingPlanResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<PricingPlanResponse> updatePricingPlan(UUID id, PricingPlanRequest request) {
        return ResponseEntity.ok(service.updatePricingPlan(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivatePricingPlan(UUID id) {
        service.deactivatePricingPlan(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<DiscountCodeResponse> createDiscountCode(DiscountCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDiscountCode(request));
    }

    @Override
    public ResponseEntity<DiscountCodeResponse> getDiscountCode(UUID id) {
        return ResponseEntity.ok(service.getDiscountCode(id));
    }

    @Override
    public ResponseEntity<PageResponseDiscountCodeResponse> listDiscountCodes(Integer page, Integer size) {
        var result = service.listDiscountCodes(page, size);
        return ResponseEntity.ok(new PageResponseDiscountCodeResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<DiscountCodeResponse> updateDiscountCode(UUID id, DiscountCodeRequest request) {
        return ResponseEntity.ok(service.updateDiscountCode(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateDiscountCode(UUID id) {
        service.deactivateDiscountCode(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RefundPolicyResponse> createRefundPolicy(RefundPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRefundPolicy(request));
    }

    @Override
    public ResponseEntity<RefundPolicyResponse> getRefundPolicy(UUID id) {
        return ResponseEntity.ok(service.getRefundPolicy(id));
    }

    @Override
    public ResponseEntity<PageResponseRefundPolicyResponse> listRefundPolicies(Integer page, Integer size) {
        var result = service.listRefundPolicies(page, size);
        return ResponseEntity.ok(new PageResponseRefundPolicyResponse()
                .items(result.items()).page(result.page()).size(result.size())
                .totalElements(result.totalElements()).totalPages(result.totalPages()));
    }

    @Override
    public ResponseEntity<RefundPolicyResponse> updateRefundPolicy(UUID id, RefundPolicyRequest request) {
        return ResponseEntity.ok(service.updateRefundPolicy(id, request));
    }

    @Override
    public ResponseEntity<Void> deactivateRefundPolicy(UUID id) {
        service.deactivateRefundPolicy(id);
        return ResponseEntity.noContent().build();
    }
}
