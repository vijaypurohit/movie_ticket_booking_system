package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.vijaypurohit.movietickets.catalog.model.Movie;

public interface MovieRepository extends JpaRepository<Movie, UUID> { }
