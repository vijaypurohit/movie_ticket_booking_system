package com.vijaypurohit.movietickets.pricing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

@Entity
public class DiscountCode extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Enumerated(EnumType.STRING) @Column(name = "discount_type", nullable = false, length = 20) private DiscountType type;
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2) private BigDecimal value;
    @Column(name = "valid_from", nullable = false) private Instant validFrom;
    @Column(name = "valid_until", nullable = false) private Instant validUntil;
    @Column(name = "minimum_spend", nullable = false, precision = 12, scale = 2) private BigDecimal minimumSpend;
    @Column(name = "maximum_discount", precision = 12, scale = 2) private BigDecimal maximumDiscount;
    @Column(name = "global_usage_limit") private Integer globalUsageLimit;
    @Column(name = "per_customer_usage_limit") private Integer perCustomerUsageLimit;
    @Column(nullable = false) private boolean active;

    protected DiscountCode() { }

    public DiscountCode(UUID id, String code, DiscountType type, BigDecimal value, Instant validFrom,
            Instant validUntil, BigDecimal minimumSpend, BigDecimal maximumDiscount,
            Integer globalUsageLimit, Integer perCustomerUsageLimit) {
        this.id = Objects.requireNonNull(id);
        update(code, type, value, validFrom, validUntil, minimumSpend, maximumDiscount,
                globalUsageLimit, perCustomerUsageLimit);
        active = true;
    }

    public void update(String code, DiscountType type, BigDecimal value, Instant validFrom,
            Instant validUntil, BigDecimal minimumSpend, BigDecimal maximumDiscount,
            Integer globalUsageLimit, Integer perCustomerUsageLimit) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        this.code = code.strip().toUpperCase(Locale.ROOT);
        this.type = Objects.requireNonNull(type);
        this.value = money(value, true, "value");
        this.validFrom = Objects.requireNonNull(validFrom);
        this.validUntil = Objects.requireNonNull(validUntil);
        if (!validUntil.isAfter(validFrom)) throw new IllegalArgumentException("validUntil must be after validFrom");
        if (type == DiscountType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("percentage cannot exceed 100");
        this.minimumSpend = money(minimumSpend, false, "minimumSpend");
        this.maximumDiscount = maximumDiscount == null ? null : money(maximumDiscount, true, "maximumDiscount");
        this.globalUsageLimit = positiveOrNull(globalUsageLimit, "globalUsageLimit");
        this.perCustomerUsageLimit = positiveOrNull(perCustomerUsageLimit, "perCustomerUsageLimit");
    }

    public void deactivate() { active = false; }
    public UUID getId() { return id; } public String getCode() { return code; }
    public DiscountType getType() { return type; } public BigDecimal getValue() { return value; }
    public Instant getValidFrom() { return validFrom; } public Instant getValidUntil() { return validUntil; }
    public BigDecimal getMinimumSpend() { return minimumSpend; } public BigDecimal getMaximumDiscount() { return maximumDiscount; }
    public Integer getGlobalUsageLimit() { return globalUsageLimit; } public Integer getPerCustomerUsageLimit() { return perCustomerUsageLimit; }
    public boolean isActive() { return active; }

    private static BigDecimal money(BigDecimal value, boolean positive, String field) {
        Objects.requireNonNull(value, field);
        if (positive ? value.signum() <= 0 : value.signum() < 0) throw new IllegalArgumentException(field + " is invalid");
        return value.setScale(2);
    }
    private static Integer positiveOrNull(Integer value, String field) {
        if (value != null && value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }
}
