package com.vijaypurohit.movietickets.booking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;

@Entity
@EntityListeners(AuditingEntityListener.class)
public class DiscountRedemption {
    @Id private UUID id;
    @Column(name = "discount_code_id", nullable = false) private UUID discountCodeId;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "booking_id", nullable = false, unique = true) private UUID bookingId;
    @Column(name = "awarded_amount", nullable = false, precision = 12, scale = 2) private BigDecimal awardedAmount;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected DiscountRedemption() { }
    public DiscountRedemption(UUID id, UUID discountCodeId, UUID customerId, UUID bookingId, BigDecimal awardedAmount) {
        this.id = Objects.requireNonNull(id); this.discountCodeId = Objects.requireNonNull(discountCodeId);
        this.customerId = Objects.requireNonNull(customerId); this.bookingId = Objects.requireNonNull(bookingId);
        this.awardedAmount = Objects.requireNonNull(awardedAmount).setScale(2);
    }
}
