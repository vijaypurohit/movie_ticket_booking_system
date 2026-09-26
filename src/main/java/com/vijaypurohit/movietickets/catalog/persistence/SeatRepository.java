package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vijaypurohit.movietickets.catalog.model.Seat;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    Page<Seat> findByAuditoriumId(UUID auditoriumId, Pageable pageable);
}
