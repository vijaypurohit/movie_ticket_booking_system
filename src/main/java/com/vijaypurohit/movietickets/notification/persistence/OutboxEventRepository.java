package com.vijaypurohit.movietickets.notification.persistence;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.vijaypurohit.movietickets.notification.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'PROCESSING' AND processing_lease_until <= :now)
            ORDER BY available_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 100
            """, nativeQuery = true)
    List<OutboxEvent> claimReady(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByBusinessKey(String businessKey);

    @Modifying
    @Query(value = """
            INSERT INTO outbox_event(id, event_type, aggregate_type, aggregate_id, business_key,
                payload, status, attempt_count, available_at, created_at, updated_at, version)
            VALUES (:id, 'BOOKING_REMINDER', 'BOOKING', :bookingId, :businessKey,
                :payload, 'PENDING', 0, :now, :now, :now, 0)
            ON CONFLICT (business_key) DO NOTHING
            """, nativeQuery = true)
    int insertReminder(@Param("id") UUID id, @Param("bookingId") UUID bookingId,
            @Param("businessKey") String businessKey, @Param("payload") String payload,
            @Param("now") Instant now);
}
