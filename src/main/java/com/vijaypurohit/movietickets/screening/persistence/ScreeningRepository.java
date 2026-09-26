package com.vijaypurohit.movietickets.screening.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

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

    @Query(value = """
            SELECT screening.* FROM screening
            JOIN auditorium ON auditorium.id = screening.auditorium_id
            JOIN theater ON theater.id = auditorium.theater_id
            WHERE screening.status = 'ACTIVE'
              AND theater.city_id = :cityId
              AND (:movieId IS NULL OR screening.movie_id = :movieId)
              AND (:theaterId IS NULL OR theater.id = :theaterId)
              AND screening.start_time >= :dayStart
              AND screening.start_time < :dayEnd
              AND (screening.start_time > :cursorTime
                   OR (screening.start_time = :cursorTime AND screening.id > :cursorId))
            ORDER BY screening.start_time, screening.id
            """, nativeQuery = true)
    List<Screening> browse(@Param("cityId") UUID cityId, @Param("movieId") UUID movieId,
            @Param("theaterId") UUID theaterId, @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd, @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") UUID cursorId, Pageable pageable);
}
