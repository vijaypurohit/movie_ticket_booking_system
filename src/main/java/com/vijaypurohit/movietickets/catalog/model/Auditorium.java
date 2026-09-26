package com.vijaypurohit.movietickets.catalog.model;

import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Auditorium extends AuditableEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "theater_id") private Theater theater;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active;

    protected Auditorium() { }
    public Auditorium(UUID id, Theater theater, String name) { this.id = Objects.requireNonNull(id); this.theater = Objects.requireNonNull(theater); update(name); active = true; }
    public void update(String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required"); this.name = name.strip(); }
    public void deactivate() { active = false; }
    public UUID getId() { return id; } public Theater getTheater() { return theater; } public String getName() { return name; } public boolean isActive() { return active; }
}
