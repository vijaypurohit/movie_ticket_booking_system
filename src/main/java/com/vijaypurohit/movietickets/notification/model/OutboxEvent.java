package com.vijaypurohit.movietickets.notification.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class OutboxEvent extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(name = "aggregate_type", nullable = false, length = 50) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "business_key", nullable = false, unique = true, length = 180) private String businessKey;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OutboxStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "processing_lease_until") private Instant processingLeaseUntil;

    protected OutboxEvent() { }
    public OutboxEvent(UUID id, String eventType, String aggregateType, UUID aggregateId,
            String businessKey, String payload, Instant availableAt) {
        this.id = Objects.requireNonNull(id); this.eventType = Objects.requireNonNull(eventType);
        this.aggregateType = Objects.requireNonNull(aggregateType); this.aggregateId = Objects.requireNonNull(aggregateId);
        this.businessKey = Objects.requireNonNull(businessKey); this.payload = Objects.requireNonNull(payload);
        this.availableAt = Objects.requireNonNull(availableAt); this.status = OutboxStatus.PENDING;
    }
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getBusinessKey() { return businessKey; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public void beginProcessing(Instant leaseUntil) {
        if (status != OutboxStatus.PENDING && status != OutboxStatus.PROCESSING) throw new IllegalStateException("event is not pending");
        status = OutboxStatus.PROCESSING; processingLeaseUntil = Objects.requireNonNull(leaseUntil); attemptCount++;
    }
    public void delivered() { status = OutboxStatus.DELIVERED; processingLeaseUntil = null; }
    public void retryAt(Instant instant) { status = OutboxStatus.PENDING; availableAt = Objects.requireNonNull(instant); processingLeaseUntil = null; }
    public void fail() { status = OutboxStatus.FAILED; processingLeaseUntil = null; }
}
