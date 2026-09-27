package com.vijaypurohit.movietickets.booking.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vijaypurohit.movietickets.booking.model.DiscountRedemption;

public interface DiscountRedemptionRepository extends JpaRepository<DiscountRedemption, UUID> {
    long countByDiscountCodeId(UUID discountCodeId);
    long countByDiscountCodeIdAndCustomerId(UUID discountCodeId, UUID customerId);
    boolean existsByBookingId(UUID bookingId);
    void deleteByBookingId(UUID bookingId);
}
