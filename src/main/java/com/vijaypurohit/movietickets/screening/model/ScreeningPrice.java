package com.vijaypurohit.movietickets.screening.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;

import com.vijaypurohit.movietickets.catalog.model.SeatCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class ScreeningPrice {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "screening_id") private Screening screening;
    @Enumerated(EnumType.STRING) @Column(name = "seat_category", nullable = false, length = 20) private SeatCategory seatCategory;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(Types.CHAR) @Column(nullable = false, length = 3) private String currency;

    protected ScreeningPrice() { }
    ScreeningPrice(UUID id, Screening screening, SeatCategory category, BigDecimal amount) {
        this.id = Objects.requireNonNull(id); this.screening = Objects.requireNonNull(screening);
        this.seatCategory = Objects.requireNonNull(category); this.amount = Objects.requireNonNull(amount).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
        currency = "INR";
    }
    public UUID getId() { return id; } public SeatCategory getSeatCategory() { return seatCategory; }
    public BigDecimal getAmount() { return amount; } public String getCurrency() { return currency; }
}
