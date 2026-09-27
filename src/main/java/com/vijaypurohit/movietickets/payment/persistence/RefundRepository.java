package com.vijaypurohit.movietickets.payment.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;

import com.vijaypurohit.movietickets.payment.model.Refund;
import com.vijaypurohit.movietickets.payment.model.RefundReason;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findByPaymentIdAndReason(UUID paymentId, RefundReason reason);
    List<Refund> findByBookingIdIn(Collection<UUID> bookingIds);

    @Query(value = """
            SELECT * FROM refund
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'PROCESSING' AND processing_lease_until <= :now)
            ORDER BY available_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 100
            """, nativeQuery = true)
    List<Refund> claimReady(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from Refund refund where refund.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select refund from Refund refund, Booking booking
            where refund.id = :id and refund.bookingId = booking.id and booking.customerId = :customerId
            """)
    Optional<Refund> findOwned(@Param("id") UUID id, @Param("customerId") UUID customerId);
}
