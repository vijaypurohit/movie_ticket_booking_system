package com.vijaypurohit.movietickets.catalog.application;

import java.time.Instant;
import java.util.UUID;

public interface FutureScreeningChecker {
    boolean hasFutureScreening(UUID auditoriumId, Instant after);
}
