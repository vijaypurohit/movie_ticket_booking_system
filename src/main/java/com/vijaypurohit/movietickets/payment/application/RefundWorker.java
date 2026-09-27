package com.vijaypurohit.movietickets.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.payment.PaymentGateway;

@Component
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundWorker {
    private final RefundService refunds; private final PaymentGateway gateway;
    public RefundWorker(RefundService refunds, PaymentGateway gateway) { this.refunds=refunds; this.gateway=gateway; }

    @Scheduled(fixedDelayString = "${app.refund.worker-delay:PT5S}")
    public void process() {
        for (var work : refunds.claimReady()) {
            refunds.complete(work.id(), gateway.refund(work.idempotencyKey(), work.amount()));
        }
    }
}
