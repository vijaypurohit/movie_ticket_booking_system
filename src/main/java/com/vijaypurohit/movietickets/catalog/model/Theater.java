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
public class Theater extends AuditableEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "city_id") private City city;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 500) private String address;
    @Column(nullable = false) private boolean active;

    protected Theater() { }
    public Theater(UUID id, City city, String name, String address) {
        this.id = Objects.requireNonNull(id); this.city = Objects.requireNonNull(city); update(name, address); active = true;
    }
    public void update(String name, String address) { this.name = text(name); this.address = text(address); }
    public void deactivate() { active = false; }
    public UUID getId() { return id; } public City getCity() { return city; } public String getName() { return name; }
    public String getAddress() { return address; } public boolean isActive() { return active; }
    private String text(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("value is required"); return value.strip(); }
}
