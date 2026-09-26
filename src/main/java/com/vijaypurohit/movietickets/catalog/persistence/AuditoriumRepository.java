package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import com.vijaypurohit.movietickets.catalog.model.Auditorium;

public interface AuditoriumRepository extends JpaRepository<Auditorium, UUID> {
    Page<Auditorium> findByTheaterId(UUID theaterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select auditorium from Auditorium auditorium join fetch auditorium.theater theater join fetch theater.city where auditorium.id = :id")
    java.util.Optional<Auditorium> findByIdForUpdate(@Param("id") UUID id);
}
