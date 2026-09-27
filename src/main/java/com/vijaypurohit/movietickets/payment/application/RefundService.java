package com.vijaypurohit.movietickets.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.identity.application.CurrentUserProvider;
import com.vijaypurohit.movietickets.notification.model.OutboxEvent;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;
import com.vijaypurohit.movietickets.payment.PaymentGateway.RefundResult;
import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.model.RefundStatus;
import com.vijaypurohit.movietickets.payment.persistence.RefundRepository;
import com.vijaypurohit.movietickets.payment.web.RefundResponses.RefundResponse;
import com.vijaypurohit.movietickets.shared.error.ResourceNotFoundException;
import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

@Service
@ConditionalOnProperty(prefix = "app.booking", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RefundService {
    private final RefundRepository refunds; private final OutboxEventRepository outbox;
    private final CurrentUserProvider users; private final IdGenerator ids; private final Clock clock;
    private final int maxAttempts;

    public RefundService(RefundRepository refunds, OutboxEventRepository outbox,
            CurrentUserProvider users, IdGenerator ids, Clock clock,
            @Value("${app.refund.max-attempts:3}") int maxAttempts) {
        this.refunds=refunds; this.outbox=outbox; this.users=users; this.ids=ids; this.clock=clock;
        this.maxAttempts=maxAttempts;
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID id) {
        Refund refund = refunds.findOwned(id, users.requireCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("not-found", "Refund not found",
                        "RESOURCE_NOT_FOUND", "The requested refund was not found."));
        return response(refund);
    }

    @Transactional
    public List<RefundWork> claimReady() {
        Instant now = clock.instant();
        return refunds.claimReady(now).stream().map(refund -> {
            refund.beginProcessing(now.plus(Duration.ofMinutes(1)));
            return new RefundWork(refund.getId(), refund.getIdempotencyKey(), refund.getAmount());
        }).toList();
    }

    @Transactional
    public void complete(UUID id, RefundResult result) {
        Refund refund = refunds.findByIdForUpdate(id).orElseThrow();
        if (refund.getStatus() != RefundStatus.PROCESSING) return;
        Instant now = clock.instant();
        if (result.succeeded()) {
            refund.succeed(result.providerReference());
            outbox.save(new OutboxEvent(ids.nextId(), "REFUND_SUCCEEDED", "REFUND", refund.getId(),
                    "refund-succeeded:" + refund.getId(), "{\"refundId\":\"" + refund.getId() + "\"}", now));
        } else if (result.retryable() && refund.getAttemptCount() < maxAttempts) {
            refund.retryAt(now.plusSeconds(1L << refund.getAttemptCount()));
        } else {
            refund.fail(result.providerReference());
            outbox.save(new OutboxEvent(ids.nextId(), "REFUND_FAILED", "REFUND", refund.getId(),
                    "refund-failed:" + refund.getId(), "{\"refundId\":\"" + refund.getId() + "\"}", now));
        }
    }

    private RefundResponse response(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getBookingId(), refund.getReason(), refund.getStatus(),
                refund.getAmount(), refund.getCurrency());
    }
    public record RefundWork(UUID id, String idempotencyKey, java.math.BigDecimal amount) { }
}
