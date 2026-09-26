package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.vijaypurohit.movietickets.catalog.model.City;

public interface CityRepository extends JpaRepository<City, UUID> {
    Page<City> findByActiveTrue(Pageable pageable);
}
