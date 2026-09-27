package com.vijaypurohit.movietickets.payment.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.vijaypurohit.movietickets.generated.api.CustomerRefundsApi;
import com.vijaypurohit.movietickets.generated.model.RefundResponse;
import com.vijaypurohit.movietickets.payment.application.RefundService;

@RestController
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundController implements CustomerRefundsApi {
    private final RefundService service;

    public RefundController(RefundService service) { this.service = service; }

    @Override
    public ResponseEntity<RefundResponse> getRefund(UUID id) {
        return ResponseEntity.ok(service.get(id));
    }
}
