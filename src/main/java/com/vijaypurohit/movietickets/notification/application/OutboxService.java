package com.vijaypurohit.movietickets.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vijaypurohit.movietickets.notification.NotificationGateway.DeliveryResult;
import com.vijaypurohit.movietickets.notification.model.OutboxEvent;
import com.vijaypurohit.movietickets.notification.model.OutboxStatus;
import com.vijaypurohit.movietickets.notification.persistence.OutboxEventRepository;

@Service
@ConditionalOnProperty(prefix = "app.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxService {
    private static final int MAX_ATTEMPTS = 5;
    private final OutboxEventRepository events; private final Clock clock;
    public OutboxService(OutboxEventRepository events, Clock clock) { this.events=events; this.clock=clock; }

    @Transactional
    public List<DeliveryWork> claimReady() {
        Instant now = clock.instant();
        return events.claimReady(now).stream().map(event -> {
            event.beginProcessing(now.plus(Duration.ofMinutes(1)));
            return new DeliveryWork(event.getId(), event.getEventType(), event.getPayload());
        }).toList();
    }

    @Transactional
    public void complete(UUID eventId, DeliveryResult result) {
        OutboxEvent event = events.findByIdForUpdate(eventId).orElseThrow();
        if (event.getStatus() != OutboxStatus.PROCESSING) return;
        if (result.succeeded()) event.delivered();
        else if (result.retryable() && event.getAttemptCount() < MAX_ATTEMPTS)
            event.retryAt(clock.instant().plusSeconds(1L << event.getAttemptCount()));
        else event.fail();
    }
    public record DeliveryWork(UUID id, String eventType, String payload) { }
}
