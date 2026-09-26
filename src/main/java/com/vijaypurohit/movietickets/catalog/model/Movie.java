package com.vijaypurohit.movietickets.catalog.model;

import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Movie extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 240) private String title;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(nullable = false, length = 80) private String language;
    @Column(nullable = false) private boolean active;

    protected Movie() { }
    public Movie(UUID id, String title, int durationMinutes, String language) { this.id = Objects.requireNonNull(id); update(title, durationMinutes, language); active = true; }
    public void update(String title, int durationMinutes, String language) { if (title == null || title.isBlank() || language == null || language.isBlank()) throw new IllegalArgumentException("title and language are required"); if (durationMinutes < 1) throw new IllegalArgumentException("durationMinutes must be positive"); this.title = title.strip(); this.durationMinutes = durationMinutes; this.language = language.strip(); }
    public void deactivate() { active = false; }
    public UUID getId() { return id; } public String getTitle() { return title; } public int getDurationMinutes() { return durationMinutes; } public String getLanguage() { return language; } public boolean isActive() { return active; }
}
