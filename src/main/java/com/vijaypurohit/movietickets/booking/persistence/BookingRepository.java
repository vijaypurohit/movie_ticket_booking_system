package com.vijaypurohit.movietickets.booking.persistence;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import com.vijaypurohit.movietickets.booking.model.Booking;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query(value = "SELECT id FROM app_user WHERE id = :customerId FOR UPDATE", nativeQuery = true)
    UUID lockCustomer(@Param("customerId") UUID customerId);

    Optional<Booking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
    Optional<Booking> findByCustomerIdAndCancellationIdempotencyKey(UUID customerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.reservationId = :reservationId")
    Optional<Booking> findByReservationIdForUpdate(@Param("reservationId") UUID reservationId);

    @EntityGraph(attributePaths = {"items", "refundRules"})
    @Query("select booking from Booking booking where booking.id = :id and booking.customerId = :customerId")
    Optional<Booking> findDetailedOwned(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "refundRules"})
    @Query("select booking from Booking booking where booking.id = :id and booking.customerId = :customerId")
    Optional<Booking> findOwnedForUpdate(@Param("id") UUID id, @Param("customerId") UUID customerId);

    @Query("""
            select booking from Booking booking
            where booking.customerId = :customerId
              and (booking.createdAt < :cursorTime
                   or (booking.createdAt = :cursorTime and booking.id < :cursorId))
            order by booking.createdAt desc, booking.id desc
            """)
    List<Booking> findHistory(@Param("customerId") UUID customerId,
            @Param("cursorTime") Instant cursorTime, @Param("cursorId") UUID cursorId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    @Query("select distinct booking from Booking booking where booking.id in :ids")
    List<Booking> findHistoryDetails(@Param("ids") List<UUID> ids);

    @Query("select booking from Booking booking where booking.state = 'PENDING_PAYMENT' and booking.checkoutExpiresAt <= :now order by booking.checkoutExpiresAt, booking.id")
    List<Booking> findExpiredPending(@Param("now") Instant now, Pageable pageable);

    @Query("select booking.id from Booking booking where booking.screeningId = :screeningId and booking.state = 'CONFIRMED' order by booking.id")
    List<UUID> findConfirmedIdsByScreening(@Param("screeningId") UUID screeningId, Pageable pageable);

    @Query(value = """
            SELECT booking.id AS bookingId, screening.start_time AS screeningStart
            FROM booking JOIN screening ON screening.id = booking.screening_id
            WHERE booking.state = 'CONFIRMED'
              AND screening.start_time >= :windowStart AND screening.start_time < :windowEnd
            ORDER BY screening.start_time, booking.id
            LIMIT 100
            """, nativeQuery = true)
    List<ReminderCandidate> findReminderCandidates(@Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    interface ReminderCandidate { UUID getBookingId(); Instant getScreeningStart(); }
}
