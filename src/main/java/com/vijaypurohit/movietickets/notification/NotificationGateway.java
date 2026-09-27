package com.vijaypurohit.movietickets.notification;

import java.util.UUID;

public interface NotificationGateway {
    DeliveryResult deliver(UUID eventId, String eventType, String payload);
    record DeliveryResult(boolean succeeded, boolean retryable) { }
}
