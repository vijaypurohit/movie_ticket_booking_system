package com.vijaypurohit.movietickets.screening.model;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
public class Screening extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "movie_id", nullable = false) private UUID movieId;
    @Column(name = "auditorium_id", nullable = false) private UUID auditoriumId;
    @Column(name = "pricing_plan_id", nullable = false) private UUID pricingPlanId;
    @Column(name = "refund_policy_id", nullable = false) private UUID refundPolicyId;
    @Column(name = "start_time", nullable = false) private Instant startTime;
    @Column(name = "end_time", nullable = false) private Instant endTime;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ScreeningStatus status;
    @OneToMany(mappedBy = "screening", cascade = CascadeType.ALL, orphanRemoval = true) private Set<ScreeningPrice> prices = new LinkedHashSet<>();
    @OneToMany(mappedBy = "screening", cascade = CascadeType.ALL, orphanRemoval = true) private Set<ScreeningSeat> seats = new LinkedHashSet<>();

    protected Screening() { }

    public Screening(UUID id, UUID movieId, UUID auditoriumId, UUID pricingPlanId, UUID refundPolicyId,
            Instant startTime, Instant endTime) {
        this.id = Objects.requireNonNull(id);
        this.movieId = Objects.requireNonNull(movieId);
        this.auditoriumId = Objects.requireNonNull(auditoriumId);
        this.pricingPlanId = Objects.requireNonNull(pricingPlanId);
        this.refundPolicyId = Objects.requireNonNull(refundPolicyId);
        reschedule(startTime, endTime);
        status = ScreeningStatus.ACTIVE;
    }

    public void addPrice(UUID id, SeatCategory category, java.math.BigDecimal amount) { prices.add(new ScreeningPrice(id, this, category, amount)); }
    public void addSeat(UUID id, UUID physicalSeatId) { seats.add(new ScreeningSeat(id, this, physicalSeatId)); }
    public void cancel() { status = ScreeningStatus.CANCELLED; }
    private void reschedule(Instant startTime, Instant endTime) {
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = Objects.requireNonNull(endTime);
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("endTime must be after startTime");
    }

    public UUID getId() { return id; } public UUID getMovieId() { return movieId; }
    public UUID getAuditoriumId() { return auditoriumId; } public UUID getPricingPlanId() { return pricingPlanId; }
    public UUID getRefundPolicyId() { return refundPolicyId; } public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; } public ScreeningStatus getStatus() { return status; }
    public List<ScreeningPrice> getPrices() { return List.copyOf(prices); }
    public List<ScreeningSeat> getSeats() { return List.copyOf(seats); }
}
