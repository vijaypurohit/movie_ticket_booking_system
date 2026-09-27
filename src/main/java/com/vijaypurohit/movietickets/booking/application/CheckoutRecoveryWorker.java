package com.vijaypurohit.movietickets.booking.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.payment.PaymentGateway;

@Component
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutRecoveryWorker {
    private final BookingTransactionService transactions;
    private final PaymentGateway gateway;

    public CheckoutRecoveryWorker(BookingTransactionService transactions, PaymentGateway gateway) {
        this.transactions = transactions; this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${app.booking.cleanup-delay:PT30S}")
    public void recoverExpiredCheckouts() {
        for (var candidate : transactions.expiredCandidates()) {
            gateway.findChargeResult(candidate.gatewayKey())
                    .ifPresentOrElse(
                            result -> transactions.finalizePayment(candidate.bookingId(), result),
                            () -> transactions.expireCheckout(candidate.bookingId()));
        }
    }
}
