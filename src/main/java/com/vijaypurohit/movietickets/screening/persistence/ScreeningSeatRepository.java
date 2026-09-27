package com.vijaypurohit.movietickets.screening.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;

import com.vijaypurohit.movietickets.screening.model.ScreeningSeat;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, UUID> {
    long countByScreeningId(UUID screeningId);

    @Query(value = """
            SELECT screening_seat.id AS id, seat.id AS seatId, seat.row_label AS rowLabel,
                   seat.seat_number AS seatNumber, seat.category AS category,
                   CASE
                     WHEN screening_seat.state = 'RESERVED' AND reservation.state = 'ACTIVE'
                          AND reservation.expires_at <= :now THEN 'AVAILABLE'
                     WHEN screening_seat.state = 'PAYMENT_IN_PROGRESS'
                          AND reservation.checkout_expires_at <= :now THEN 'AVAILABLE'
                     ELSE screening_seat.state
                   END AS state
            FROM screening_seat
            JOIN seat ON seat.id = screening_seat.seat_id
            LEFT JOIN seat_reservation reservation ON reservation.id = screening_seat.reservation_id
            WHERE screening_seat.screening_id = :screeningId
            ORDER BY seat.row_label, seat.seat_number, screening_seat.id
            """, nativeQuery = true)
    List<SeatAvailabilityProjection> findAvailability(@Param("screeningId") UUID screeningId,
            @Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select seat from ScreeningSeat seat where seat.id in :ids order by seat.id")
    List<ScreeningSeat> findAllByIdForUpdate(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select seat from ScreeningSeat seat where seat.reservationId = :reservationId order by seat.id")
    List<ScreeningSeat> findByReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    List<ScreeningSeat> findByReservationIdOrderById(UUID reservationId);

    interface SeatAvailabilityProjection {
        UUID getId(); UUID getSeatId(); String getRowLabel(); int getSeatNumber();
        String getCategory(); String getState();
    }
}
