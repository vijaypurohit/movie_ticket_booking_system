package com.vijaypurohit.movietickets.payment.model;

import java.math.BigDecimal;
import java.sql.Types;
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
public class Payment extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false, unique = true) private UUID bookingId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus status;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "gateway_idempotency_key", nullable = false, unique = true, length = 160) private String gatewayIdempotencyKey;
    @Column(name = "provider_reference", length = 100) private String providerReference;

    protected Payment() { }
    public Payment(UUID id, UUID bookingId, BigDecimal amount, String gatewayIdempotencyKey) {
        this.id = Objects.requireNonNull(id); this.bookingId = Objects.requireNonNull(bookingId);
        this.amount = Objects.requireNonNull(amount).setScale(2); this.gatewayIdempotencyKey = Objects.requireNonNull(gatewayIdempotencyKey);
        this.currency = "INR"; this.status = PaymentStatus.INITIATED;
    }
    public void succeed(String reference) { if (status == PaymentStatus.INITIATED || isExpiredAttempt()) { status = PaymentStatus.SUCCEEDED; providerReference = reference; } }
    public void decline(String reference) { if (status == PaymentStatus.INITIATED) { status = PaymentStatus.DECLINED; providerReference = reference; } }
    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getGatewayIdempotencyKey() { return gatewayIdempotencyKey; }
    public boolean isExpiredAttempt() { return status == PaymentStatus.DECLINED && "checkout-expired".equals(providerReference); }
}
