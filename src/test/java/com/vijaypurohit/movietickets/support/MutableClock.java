package com.vijaypurohit.movietickets.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = new AtomicReference<>(Objects.requireNonNull(instant));
        this.zone = Objects.requireNonNull(zone);
    }

    public void set(Instant value) {
        instant.set(Objects.requireNonNull(value));
    }

    public void advance(Duration duration) {
        instant.updateAndGet(value -> value.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId requestedZone) {
        return new MutableClock(instant(), requestedZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
