package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vijaypurohit.movietickets.catalog.model.Auditorium;

public interface AuditoriumRepository extends JpaRepository<Auditorium, UUID> {
    Page<Auditorium> findByTheaterId(UUID theaterId, Pageable pageable);
}
