package com.vijaypurohit.movietickets.payment.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.vijaypurohit.movietickets.payment.model.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from Payment payment where payment.bookingId = :bookingId")
    Optional<Payment> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
    List<Payment> findByBookingIdIn(Collection<UUID> bookingIds);
}
