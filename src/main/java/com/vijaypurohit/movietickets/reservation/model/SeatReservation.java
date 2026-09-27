package com.vijaypurohit.movietickets.reservation.model;

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
public class SeatReservation extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "screening_id", nullable = false) private UUID screeningId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReservationState state;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "checkout_expires_at") private Instant checkoutExpiresAt;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;

    protected SeatReservation() { }
    public SeatReservation(UUID id, UUID customerId, UUID screeningId, Instant expiresAt,
            String idempotencyKey, String requestFingerprint) {
        this.id = Objects.requireNonNull(id); this.customerId = Objects.requireNonNull(customerId);
        this.screeningId = Objects.requireNonNull(screeningId); this.expiresAt = Objects.requireNonNull(expiresAt);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey); this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        state = ReservationState.ACTIVE;
    }
    public boolean isExpired(Instant now) { return state == ReservationState.ACTIVE && !expiresAt.isAfter(now); }
    public boolean isCheckoutExpired(Instant now) { return state == ReservationState.PAYMENT_IN_PROGRESS && !checkoutExpiresAt.isAfter(now); }
    public void beginCheckout(Instant deadline) {
        if (state != ReservationState.ACTIVE) throw new IllegalStateException("reservation is not active");
        checkoutExpiresAt = Objects.requireNonNull(deadline);
        state = ReservationState.PAYMENT_IN_PROGRESS;
    }
    public void convert() {
        if (state != ReservationState.PAYMENT_IN_PROGRESS) throw new IllegalStateException("reservation is not checking out");
        state = ReservationState.CONVERTED;
    }
    public void failCheckout() {
        if (state == ReservationState.PAYMENT_IN_PROGRESS) state = ReservationState.RELEASED;
    }
    public void expire() { if (state == ReservationState.ACTIVE) state = ReservationState.EXPIRED; }
    public void release() { if (state == ReservationState.ACTIVE || state == ReservationState.EXPIRED) state = ReservationState.RELEASED; }
    public UUID getId() { return id; } public UUID getCustomerId() { return customerId; }
    public UUID getScreeningId() { return screeningId; } public ReservationState getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; } public Instant getCheckoutExpiresAt() { return checkoutExpiresAt; }
    public String getIdempotencyKey() { return idempotencyKey; } public String getRequestFingerprint() { return requestFingerprint; }
}
