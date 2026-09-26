package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vijaypurohit.movietickets.catalog.model.Theater;

public interface TheaterRepository extends JpaRepository<Theater, UUID> {
    Page<Theater> findByCityId(UUID cityId, Pageable pageable);
}
