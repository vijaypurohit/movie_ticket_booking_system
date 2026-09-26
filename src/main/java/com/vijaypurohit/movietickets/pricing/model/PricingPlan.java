package com.vijaypurohit.movietickets.pricing.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class PricingPlan extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(name = "regular_price", nullable = false, precision = 12, scale = 2) private BigDecimal regularPrice;
    @Column(name = "premium_price", nullable = false, precision = 12, scale = 2) private BigDecimal premiumPrice;
    @Column(name = "weekend_adjustment", nullable = false, precision = 12, scale = 2) private BigDecimal weekendAdjustment;
    @Column(nullable = false) private boolean active;

    protected PricingPlan() { }

    public PricingPlan(UUID id, String name, BigDecimal regularPrice, BigDecimal premiumPrice,
            BigDecimal weekendAdjustment) {
        this.id = Objects.requireNonNull(id);
        update(name, regularPrice, premiumPrice, weekendAdjustment);
        this.active = true;
    }

    public void update(String name, BigDecimal regularPrice, BigDecimal premiumPrice,
            BigDecimal weekendAdjustment) {
        this.name = requireText(name, "name");
        this.regularPrice = money(regularPrice, true, "regularPrice");
        this.premiumPrice = money(premiumPrice, true, "premiumPrice");
        this.weekendAdjustment = money(weekendAdjustment, false, "weekendAdjustment");
    }

    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getRegularPrice() { return regularPrice; }
    public BigDecimal getPremiumPrice() { return premiumPrice; }
    public BigDecimal getWeekendAdjustment() { return weekendAdjustment; }
    public boolean isActive() { return active; }

    private static BigDecimal money(BigDecimal value, boolean positive, String field) {
        Objects.requireNonNull(value, field);
        if (positive ? value.signum() <= 0 : value.signum() < 0) throw new IllegalArgumentException(field + " is invalid");
        return value.setScale(2);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
