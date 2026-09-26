package com.vijaypurohit.movietickets.catalog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import com.vijaypurohit.movietickets.catalog.model.Movie;

public interface MovieRepository extends JpaRepository<Movie, UUID> {
    @Query(value = """
            SELECT movie.* FROM movie
            WHERE movie.active = TRUE
              AND EXISTS (
                SELECT 1 FROM screening
                JOIN auditorium ON auditorium.id = screening.auditorium_id
                JOIN theater ON theater.id = auditorium.theater_id
                WHERE screening.movie_id = movie.id
                  AND theater.city_id = :cityId
                  AND screening.status = 'ACTIVE'
                  AND screening.start_time >= :dayStart
                  AND screening.start_time < :dayEnd)
            ORDER BY movie.title, movie.id
            """,
            countQuery = """
            SELECT count(*) FROM movie
            WHERE movie.active = TRUE
              AND EXISTS (
                SELECT 1 FROM screening
                JOIN auditorium ON auditorium.id = screening.auditorium_id
                JOIN theater ON theater.id = auditorium.theater_id
                WHERE screening.movie_id = movie.id
                  AND theater.city_id = :cityId
                  AND screening.status = 'ACTIVE'
                  AND screening.start_time >= :dayStart
                  AND screening.start_time < :dayEnd)
            """, nativeQuery = true)
    Page<Movie> findAvailable(@Param("cityId") UUID cityId, @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd, Pageable pageable);
}
