package com.vijaypurohit.movietickets.payment.model;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class Refund extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(name = "payment_id", nullable = false) private UUID paymentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RefundReason reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RefundStatus status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 160) private String idempotencyKey;
    @Column(name = "provider_reference", length = 100) private String providerReference;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "processing_lease_until") private Instant processingLeaseUntil;

    protected Refund() { }
    public Refund(UUID id, UUID bookingId, UUID paymentId, RefundReason reason, BigDecimal amount,
            String idempotencyKey, Instant availableAt) {
        this.id = Objects.requireNonNull(id); this.bookingId = Objects.requireNonNull(bookingId);
        this.paymentId = Objects.requireNonNull(paymentId); this.reason = Objects.requireNonNull(reason);
        this.amount = Objects.requireNonNull(amount).setScale(2); this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.availableAt = Objects.requireNonNull(availableAt); this.currency = "INR"; this.status = RefundStatus.PENDING;
    }
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public RefundReason getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public UUID getPaymentId() { return paymentId; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAttemptCount() { return attemptCount; }
    public void beginProcessing(Instant leaseUntil) {
        if (status != RefundStatus.PENDING && status != RefundStatus.PROCESSING) throw new IllegalStateException("refund is not pending");
        status = RefundStatus.PROCESSING; processingLeaseUntil = Objects.requireNonNull(leaseUntil); attemptCount++;
    }
    public void succeed(String reference) { status = RefundStatus.SUCCEEDED; providerReference = reference; processingLeaseUntil = null; }
    public void fail(String reference) { status = RefundStatus.FAILED; providerReference = reference; processingLeaseUntil = null; }
    public void retryAt(Instant instant) { status = RefundStatus.PENDING; availableAt = Objects.requireNonNull(instant); processingLeaseUntil = null; }
}
