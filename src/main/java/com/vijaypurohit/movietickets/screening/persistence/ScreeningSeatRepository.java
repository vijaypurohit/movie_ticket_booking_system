package com.vijaypurohit.movietickets.screening.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import com.vijaypurohit.movietickets.screening.model.ScreeningSeat;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, UUID> {
    long countByScreeningId(UUID screeningId);

    @Query(value = """
            SELECT screening_seat.id AS id, seat.id AS seatId, seat.row_label AS rowLabel,
                   seat.seat_number AS seatNumber, seat.category AS category,
                   screening_seat.state AS state
            FROM screening_seat
            JOIN seat ON seat.id = screening_seat.seat_id
            WHERE screening_seat.screening_id = :screeningId
            ORDER BY seat.row_label, seat.seat_number, screening_seat.id
            """, nativeQuery = true)
    List<SeatAvailabilityProjection> findAvailability(@Param("screeningId") UUID screeningId);

    interface SeatAvailabilityProjection {
        UUID getId(); UUID getSeatId(); String getRowLabel(); int getSeatNumber();
        String getCategory(); String getState();
    }
}
