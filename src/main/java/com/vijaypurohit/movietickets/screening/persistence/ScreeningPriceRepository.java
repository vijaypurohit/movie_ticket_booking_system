package com.vijaypurohit.movietickets.screening.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vijaypurohit.movietickets.screening.model.ScreeningPrice;

public interface ScreeningPriceRepository extends JpaRepository<ScreeningPrice, UUID> { }
