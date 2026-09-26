package com.vijaypurohit.movietickets.catalog.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Seat extends AuditableEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "auditorium_id") private Auditorium auditorium;
    @Column(name = "row_label", nullable = false, length = 10) private String rowLabel;
    @Column(name = "seat_number", nullable = false) private int seatNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SeatCategory category;
    @Column(nullable = false) private boolean active;

    protected Seat() { }
    public Seat(UUID id, Auditorium auditorium, String rowLabel, int seatNumber, SeatCategory category) { this.id = Objects.requireNonNull(id); this.auditorium = Objects.requireNonNull(auditorium); update(rowLabel, seatNumber, category); active = true; }
    public void update(String rowLabel, int seatNumber, SeatCategory category) { if (rowLabel == null || rowLabel.isBlank()) throw new IllegalArgumentException("rowLabel is required"); if (seatNumber < 1) throw new IllegalArgumentException("seatNumber must be positive"); this.rowLabel = rowLabel.strip().toUpperCase(Locale.ROOT); this.seatNumber = seatNumber; this.category = Objects.requireNonNull(category); }
    public void deactivate() { active = false; }
    public UUID getId() { return id; } public Auditorium getAuditorium() { return auditorium; } public String getRowLabel() { return rowLabel; } public int getSeatNumber() { return seatNumber; } public SeatCategory getCategory() { return category; } public boolean isActive() { return active; }
}
