package com.vijaypurohit.movietickets.payment.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.payment.application.RefundService;
import com.vijaypurohit.movietickets.payment.web.RefundResponses.RefundResponse;
import com.vijaypurohit.movietickets.shared.openapi.OpenApiConfiguration;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/v1/refunds")
@SecurityRequirement(name = OpenApiConfiguration.BASIC_AUTH_SCHEME)
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundController {
    private final RefundService service;
    public RefundController(RefundService service) { this.service = service; }
    @GetMapping("/{id}") public RefundResponse get(@PathVariable UUID id) { return service.get(id); }
}
