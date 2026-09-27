package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vijaypurohit.movietickets.identity.model.Role;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

class ReservationConcurrencyIT extends ApiIntegrationTest {
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwords;

    @Test
    @Timeout(90)
    void exactlyOneOfTwentyFiveCustomersCanReserveTheSameSeat() throws Exception {
        Scenario scenario = createScenario(1, false);
        List<String> emails = createCustomers(25, "same-seat");
        List<Request> requests = new ArrayList<>();
        for (int index = 0; index < emails.size(); index++) {
            requests.add(new Request(emails.get(index), List.of(scenario.screeningSeatIds().getFirst()),
                    "same-seat-" + index));
        }

        List<Integer> statuses = runSimultaneously(requests);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(24);
        assertThat(jdbc.queryForObject("select count(*) from seat_reservation where state = 'ACTIVE'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from screening_seat where reservation_id is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @Timeout(60)
    void reversedRequestsFinishWithoutDeadlockWhileDisjointSeatsProceed() throws Exception {
        Scenario scenario = createScenario(4, false);
        List<String> emails = createCustomers(4, "seat-order");
        String first = scenario.screeningSeatIds().get(0);
        String second = scenario.screeningSeatIds().get(1);

        List<Integer> reversed = runSimultaneously(List.of(
                new Request(emails.get(0), List.of(first, second), "forward"),
                new Request(emails.get(1), List.of(second, first), "reverse")));
        assertThat(reversed).containsExactlyInAnyOrder(201, 409);

        List<Integer> disjoint = runSimultaneously(List.of(
                new Request(emails.get(2), List.of(scenario.screeningSeatIds().get(2)), "disjoint-a"),
                new Request(emails.get(3), List.of(scenario.screeningSeatIds().get(3)), "disjoint-b")));
        assertThat(disjoint).containsOnly(201);
    }

    private List<String> createCustomers(int count, String prefix) {
        String hash = passwords.encode(CUSTOMER_PASSWORD);
        List<UserAccount> accounts = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String email = prefix + '-' + index + "@movietickets.local";
            emails.add(email);
            accounts.add(new UserAccount(UUID.randomUUID(), email, hash, Role.CUSTOMER));
        }
        users.saveAllAndFlush(accounts);
        return emails;
    }

    private List<Integer> runSimultaneously(List<Request> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = requests.stream().map(request -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                return post("/api/v1/seat-reservations", Map.of("screeningSeatIds", request.seatIds()),
                        request.email(), CUSTOMER_PASSWORD, request.key()).status();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(75, TimeUnit.SECONDS));
            return statuses;
        }
    }

    private record Request(String email, List<String> seatIds, String key) { }
}
