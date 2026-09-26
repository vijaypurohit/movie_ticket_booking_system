package com.vijaypurohit.movietickets.pricing.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.vijaypurohit.movietickets.shared.persistence.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

@Entity
public class RefundPolicy extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(nullable = false) private boolean active;
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cutoffMinutes DESC")
    private List<RefundPolicyRule> rules = new ArrayList<>();

    protected RefundPolicy() { }

    public RefundPolicy(UUID id, String name) {
        this.id = Objects.requireNonNull(id);
        rename(name);
        active = true;
    }

    public void replace(String name, List<RuleDefinition> definitions) {
        rename(name);
        if (definitions == null || definitions.isEmpty()) throw new IllegalArgumentException("at least one refund rule is required");
        long distinctCutoffs = definitions.stream().map(RuleDefinition::cutoffMinutes).distinct().count();
        if (distinctCutoffs != definitions.size()) throw new IllegalArgumentException("refund cutoffs must be unique");
        var requestedCutoffs = new HashSet<>(definitions.stream().map(RuleDefinition::cutoffMinutes).toList());
        rules.removeIf(rule -> !requestedCutoffs.contains(rule.getCutoffMinutes()));
        for (RuleDefinition definition : definitions) {
            rules.stream().filter(rule -> rule.getCutoffMinutes() == definition.cutoffMinutes()).findFirst()
                    .ifPresentOrElse(
                            rule -> rule.changePercentage(definition.refundPercentage()),
                            () -> rules.add(new RefundPolicyRule(definition.id(), this,
                                    definition.cutoffMinutes(), definition.refundPercentage())));
        }
        rules.sort(Comparator.comparingLong(RefundPolicyRule::getCutoffMinutes).reversed());
    }

    public void deactivate() { active = false; }
    public UUID getId() { return id; } public String getName() { return name; }
    public boolean isActive() { return active; } public List<RefundPolicyRule> getRules() { return List.copyOf(rules); }

    private void rename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        this.name = name.strip();
    }

    public record RuleDefinition(UUID id, long cutoffMinutes, BigDecimal refundPercentage) { }
}
