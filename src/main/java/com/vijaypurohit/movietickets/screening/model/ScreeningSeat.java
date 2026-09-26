package com.vijaypurohit.movietickets.screening.model;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class ScreeningSeat {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "screening_id") private Screening screening;
    @Column(name = "seat_id", nullable = false) private UUID seatId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ScreeningSeatState state;

    protected ScreeningSeat() { }
    ScreeningSeat(UUID id, Screening screening, UUID seatId) {
        this.id = Objects.requireNonNull(id); this.screening = Objects.requireNonNull(screening);
        this.seatId = Objects.requireNonNull(seatId); state = ScreeningSeatState.AVAILABLE;
    }
    public UUID getId() { return id; } public UUID getSeatId() { return seatId; }
    public ScreeningSeatState getState() { return state; }
}
