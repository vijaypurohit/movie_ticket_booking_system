package com.vijaypurohit.movietickets.pricing.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class RefundPolicyRule {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "refund_policy_id") private RefundPolicy policy;
    @Column(name = "cutoff_minutes", nullable = false) private long cutoffMinutes;
    @Column(name = "refund_percentage", nullable = false, precision = 5, scale = 2) private BigDecimal refundPercentage;

    protected RefundPolicyRule() { }

    RefundPolicyRule(UUID id, RefundPolicy policy, long cutoffMinutes, BigDecimal refundPercentage) {
        this.id = Objects.requireNonNull(id);
        this.policy = Objects.requireNonNull(policy);
        if (cutoffMinutes < 0) throw new IllegalArgumentException("cutoffMinutes cannot be negative");
        this.cutoffMinutes = cutoffMinutes;
        changePercentage(refundPercentage);
    }

    void changePercentage(BigDecimal refundPercentage) {
        Objects.requireNonNull(refundPercentage);
        if (refundPercentage.signum() < 0 || refundPercentage.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("refundPercentage must be between 0 and 100");
        this.refundPercentage = refundPercentage.setScale(2);
    }

    public UUID getId() { return id; }
    public long getCutoffMinutes() { return cutoffMinutes; }
    public BigDecimal getRefundPercentage() { return refundPercentage; }
}
