package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vijaypurohit.movietickets.catalog.model.City;

public interface CityRepository extends JpaRepository<City, UUID> { }
