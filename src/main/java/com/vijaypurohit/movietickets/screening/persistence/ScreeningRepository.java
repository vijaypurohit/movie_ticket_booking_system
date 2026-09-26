package com.vijaypurohit.movietickets.screening.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vijaypurohit.movietickets.screening.model.Screening;
import com.vijaypurohit.movietickets.screening.model.ScreeningStatus;

public interface ScreeningRepository extends JpaRepository<Screening, UUID> {
    @Query("select (count(screening) > 0) from Screening screening where screening.auditoriumId = :auditoriumId and screening.status = :status and screening.startTime < :endTime and screening.endTime > :startTime")
    boolean existsOverlap(@Param("auditoriumId") UUID auditoriumId, @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime, @Param("status") ScreeningStatus status);

    boolean existsByAuditoriumIdAndStatusAndStartTimeAfter(UUID auditoriumId, ScreeningStatus status, Instant startTime);

    @EntityGraph(attributePaths = {"prices", "seats"})
    @Query("select screening from Screening screening where screening.id = :id")
    Optional<Screening> findDetailedById(@Param("id") UUID id);
}
