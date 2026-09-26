package com.vijaypurohit.movietickets.pricing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vijaypurohit.movietickets.pricing.model.PricingPlan;

public interface PricingPlanRepository extends JpaRepository<PricingPlan, UUID> { }
