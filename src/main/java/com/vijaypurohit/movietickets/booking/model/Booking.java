package com.vijaypurohit.movietickets.booking.model;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;
import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class Booking extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 24) private String reference;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "screening_id", nullable = false) private UUID screeningId;
    @Column(name = "reservation_id", nullable = false, unique = true) private UUID reservationId;
    @Column(name = "discount_code_id") private UUID discountCodeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private BookingState state;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2) private BigDecimal discountAmount;
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2) private BigDecimal totalAmount;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;
    @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "checkout_expires_at", nullable = false) private Instant checkoutExpiresAt;
    @Column(name = "cancellation_idempotency_key", length = 120) private String cancellationIdempotencyKey;
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true) private Set<BookingItem> items = new LinkedHashSet<>();
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true) private Set<BookingRefundRule> refundRules = new LinkedHashSet<>();

    protected Booking() { }

    public Booking(UUID id, String reference, UUID customerId, UUID screeningId, UUID reservationId,
            UUID discountCodeId, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
            String idempotencyKey, String requestFingerprint, Instant checkoutExpiresAt) {
        this.id = Objects.requireNonNull(id); this.reference = Objects.requireNonNull(reference);
        this.customerId = Objects.requireNonNull(customerId); this.screeningId = Objects.requireNonNull(screeningId);
        this.reservationId = Objects.requireNonNull(reservationId); this.discountCodeId = discountCodeId;
        this.subtotal = money(subtotal); this.discountAmount = money(discountAmount); this.totalAmount = money(totalAmount);
        if (this.totalAmount.compareTo(this.subtotal.subtract(this.discountAmount)) != 0) throw new IllegalArgumentException("invalid total");
        this.currency = "INR"; this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint);
        this.checkoutExpiresAt = Objects.requireNonNull(checkoutExpiresAt); this.state = BookingState.PENDING_PAYMENT;
    }

    public void addItem(UUID id, UUID screeningSeatId, String rowLabel, int seatNumber,
            SeatCategory category, BigDecimal unitPrice) {
        items.add(new BookingItem(id, this, screeningSeatId, rowLabel, seatNumber, category, unitPrice));
    }
    public void addRefundRule(UUID id, long cutoffMinutes, BigDecimal percentage) {
        refundRules.add(new BookingRefundRule(id, this, cutoffMinutes, percentage));
    }
    public void confirm() {
        if (state != BookingState.PENDING_PAYMENT) throw new IllegalStateException("booking is not pending payment");
        state = BookingState.CONFIRMED;
    }
    public void fail() { if (state == BookingState.PENDING_PAYMENT) state = BookingState.FAILED; }
    public void cancel(String idempotencyKey) {
        if (state != BookingState.CONFIRMED) throw new IllegalStateException("booking is not confirmed");
        cancellationIdempotencyKey = Objects.requireNonNull(idempotencyKey);
        state = BookingState.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public UUID getCustomerId() { return customerId; }
    public UUID getScreeningId() { return screeningId; }
    public UUID getReservationId() { return reservationId; }
    public UUID getDiscountCodeId() { return discountCodeId; }
    public BookingState getState() { return state; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getCheckoutExpiresAt() { return checkoutExpiresAt; }
    public String getCancellationIdempotencyKey() { return cancellationIdempotencyKey; }
    public Set<BookingItem> getItems() { return Set.copyOf(items); }
    public Set<BookingRefundRule> getRefundRules() { return Set.copyOf(refundRules); }
    private static BigDecimal money(BigDecimal value) { return Objects.requireNonNull(value).setScale(2); }
}
