package com.vijaypurohit.movietickets.reservation.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

import com.vijaypurohit.movietickets.reservation.model.SeatReservation;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, UUID> {
    Optional<SeatReservation> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
    Optional<SeatReservation> findByIdAndCustomerId(UUID id, UUID customerId);

    @Query(value = """
            SELECT * FROM seat_reservation
            WHERE state = 'ACTIVE' AND expires_at <= :now
            ORDER BY expires_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 100
            """, nativeQuery = true)
    List<SeatReservation> claimExpired(@Param("now") Instant now);
}
