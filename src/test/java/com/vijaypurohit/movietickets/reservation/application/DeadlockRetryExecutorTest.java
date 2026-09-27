package com.vijaypurohit.movietickets.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class DeadlockRetryExecutorTest {
    private final List<Long> pauses = new ArrayList<>();
    private final DeadlockRetryExecutor retries = new DeadlockRetryExecutor(
            attempt -> attempt * 7L, pauses::add);

    @Test
    void retriesTwiceThenReturnsTheSuccessfulResult() {
        AtomicInteger attempts = new AtomicInteger();

        String result = retries.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw deadlock();
            return "reserved";
        });

        assertThat(result).isEqualTo("reserved");
        assertThat(attempts).hasValue(3);
        assertThat(pauses).containsExactly(7L, 14L);
    }

    @Test
    void stopsAfterTheThirdDeadlock() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.execute(() -> {
            attempts.incrementAndGet();
            throw deadlock();
        })).isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(attempts).hasValue(3);
        assertThat(pauses).containsExactly(7L, 14L);
    }

    @Test
    void doesNotRetryAnotherDatabaseFailure() {
        AtomicInteger attempts = new AtomicInteger();
        var failure = new DataAccessResourceFailureException("connection unavailable",
                new SQLException("connection", "08006"));

        assertThatThrownBy(() -> retries.execute(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(attempts).hasValue(1);
        assertThat(pauses).isEmpty();
    }

    private DataAccessResourceFailureException deadlock() {
        return new DataAccessResourceFailureException("deadlock",
                new SQLException("deadlock detected", DeadlockRetryExecutor.POSTGRES_DEADLOCK_STATE));
    }
}
