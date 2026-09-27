package com.vijaypurohit.movietickets.reservation.application;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntToLongFunction;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class DeadlockRetryExecutor {
    static final String POSTGRES_DEADLOCK_STATE = "40P01";
    private static final int MAX_ATTEMPTS = 3;

    private final IntToLongFunction jitterMillis;
    private final Sleeper sleeper;

    public DeadlockRetryExecutor() {
        this(ignored -> ThreadLocalRandom.current().nextLong(5, 21), Thread::sleep);
    }

    DeadlockRetryExecutor(IntToLongFunction jitterMillis, Sleeper sleeper) {
        this.jitterMillis = Objects.requireNonNull(jitterMillis);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    public <T> T execute(Supplier<T> command) {
        Objects.requireNonNull(command);
        for (int attempt = 1; ; attempt++) {
            try {
                return command.get();
            } catch (DataAccessException exception) {
                if (!isPostgresDeadlock(exception) || attempt >= MAX_ATTEMPTS) throw exception;
                pause(jitterMillis.applyAsLong(attempt));
            }
        }
    }

    boolean isPostgresDeadlock(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && POSTGRES_DEADLOCK_STATE.equals(sqlException.getSQLState())) return true;
            current = current.getCause();
        }
        return false;
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(Math.max(0, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a deadlocked reservation", exception);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
