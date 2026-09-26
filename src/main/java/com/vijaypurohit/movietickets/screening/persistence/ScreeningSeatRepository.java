package com.vijaypurohit.movietickets.screening.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vijaypurohit.movietickets.screening.model.ScreeningSeat;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, UUID> {
    long countByScreeningId(UUID screeningId);
}
