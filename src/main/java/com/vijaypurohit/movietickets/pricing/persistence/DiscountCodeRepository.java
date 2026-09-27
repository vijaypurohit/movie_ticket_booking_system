package com.vijaypurohit.movietickets.pricing.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.vijaypurohit.movietickets.pricing.model.DiscountCode;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, UUID> {
    Optional<DiscountCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select discount from DiscountCode discount where discount.code = :code")
    Optional<DiscountCode> findByCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select discount from DiscountCode discount where discount.id = :id")
    Optional<DiscountCode> findByIdForUpdate(@Param("id") UUID id);
}
