package com.vijaypurohit.movietickets.catalog.model;

import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class City extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 120) private String country;
    @Column(name = "time_zone", nullable = false, length = 80) private String timeZone;
    @Column(nullable = false) private boolean active;

    protected City() { }

    public City(UUID id, String name, String country, String timeZone) {
        this.id = Objects.requireNonNull(id);
        update(name, country, timeZone);
        this.active = true;
    }

    public void update(String name, String country, String timeZone) {
        this.name = requireText(name, "name");
        this.country = requireText(country, "country");
        this.timeZone = ZoneId.of(requireText(timeZone, "timeZone")).getId();
    }

    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getTimeZone() { return timeZone; }
    public boolean isActive() { return active; }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
