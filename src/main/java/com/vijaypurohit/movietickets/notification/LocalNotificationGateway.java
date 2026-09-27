package com.vijaypurohit.movietickets.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalNotificationGateway implements NotificationGateway {
    private final ConcurrentHashMap<UUID, Delivery> deliveries = new ConcurrentHashMap<>();
    private final Clock clock;
    public LocalNotificationGateway(Clock clock) { this.clock = clock; }

    @Override
    public DeliveryResult deliver(UUID eventId, String eventType, String payload) {
        if (eventType.startsWith("FORCE_RETRY")) return new DeliveryResult(false, true);
        if (eventType.startsWith("FORCE_FAIL")) return new DeliveryResult(false, false);
        deliveries.putIfAbsent(eventId, new Delivery(eventId, eventType, payload, clock.instant()));
        return new DeliveryResult(true, false);
    }
    public List<Delivery> deliveries() { return List.copyOf(deliveries.values()); }
    public record Delivery(UUID eventId, String eventType, String payload, Instant deliveredAt) { }
}
