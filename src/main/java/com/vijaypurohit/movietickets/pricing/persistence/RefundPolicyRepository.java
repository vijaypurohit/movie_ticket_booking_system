package com.vijaypurohit.movietickets.pricing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.vijaypurohit.movietickets.pricing.model.RefundPolicy;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, UUID> {
    @Override
    @EntityGraph(attributePaths = "rules")
    java.util.Optional<RefundPolicy> findById(UUID id);
}
