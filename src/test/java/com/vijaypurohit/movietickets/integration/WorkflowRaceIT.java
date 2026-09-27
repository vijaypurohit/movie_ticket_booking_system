package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vijaypurohit.movietickets.booking.application.CheckoutRecoveryWorker;
import com.vijaypurohit.movietickets.identity.model.Role;
import com.vijaypurohit.movietickets.identity.model.UserAccount;
import com.vijaypurohit.movietickets.identity.persistence.UserAccountRepository;
import com.vijaypurohit.movietickets.notification.application.OutboxService;
import com.vijaypurohit.movietickets.reservation.application.SeatReservationService;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;
import com.vijaypurohit.movietickets.support.ScriptedPaymentGateway;
import com.vijaypurohit.movietickets.support.TestGatewayConfiguration;

/**
 * The races that {@code docs/TESTING.md} section 5 requires. Each one drives a real competing
 * operation rather than asserting on a mocked interleaving.
 */
@Import(TestGatewayConfiguration.class)
class WorkflowRaceIT extends ApiIntegrationTest {
    @Autowired private ScriptedPaymentGateway gateway;
    @Autowired private CheckoutRecoveryWorker recovery;
    @Autowired private SeatReservationService reservations;
    @Autowired private OutboxService outbox;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder passwords;

    @org.junit.jupiter.api.BeforeEach
    void resetGateway() { gateway.reset(); }

    @Test
    @Timeout(120)
    void lateGatewaySuccessCannotConfirmOrReclaimASeatResoldAfterTheLeaseExpired() throws Exception {
        Scenario scenario = createScenario(1, false);
        String seatId = scenario.screeningSeatIds().getFirst();
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(seatId)),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "race-reservation"), 201);

        // Park the charge inside the gateway; the checkout transaction has already committed, so
        // the seat is held only by the one-minute lease while we are blocked here.
        gateway.gateNextCharge();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ApiResponse> booking = executor.submit(() -> post("/api/v1/bookings", Map.of(
                    "reservationId", reservation.body().get("id").asText(),
                    "paymentToken", "tok_success"),
                    CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "race-booking"));

            gateway.awaitChargeEntered();
            clock.advance(Duration.ofSeconds(90));

            // A second customer legitimately takes the seat once the lease has lapsed.
            ApiResponse resale = requireStatus(post("/api/v1/seat-reservations",
                    Map.of("screeningSeatIds", List.of(seatId)),
                    CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "race-resale"), 201);

            gateway.releaseCharge();
            ApiResponse result = booking.get(90, TimeUnit.SECONDS);

            assertThat(result.body().get("state").asText()).isEqualTo("FAILED");
            assertThat(result.body().get("paymentStatus").asText()).isEqualTo("SUCCEEDED");

            // The seat must still belong to the second customer, never be reclaimed.
            assertThat(seatOwner(seatId)).isEqualTo(resale.body().get("id").asText());
            assertThat(seatState(seatId)).isEqualTo("RESERVED");
        }

        assertThat(count("SELECT count(*) FROM booking WHERE state = 'CONFIRMED'")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'BOOKING_CONFIRMED'")).isZero();
        assertThat(count("SELECT count(*) FROM refund WHERE reason = 'LATE_PAYMENT'"))
                .as("exactly one compensating refund").isEqualTo(1);

        // Repeat finalization to prove the compensating refund is not duplicated.
        recovery.recoverExpiredCheckouts();
        recovery.recoverExpiredCheckouts();
        assertThat(count("SELECT count(*) FROM refund WHERE reason = 'LATE_PAYMENT'")).isEqualTo(1);
    }

    @Test
    @Timeout(120)
    void concurrentBookingsCannotExceedTheLastDiscountRedemption() throws Exception {
        Scenario scenario = createScenario(2, false);
        String code = "LASTONE" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Map<String, Object> discount = new LinkedHashMap<>();
        discount.put("code", code);
        discount.put("type", "PERCENTAGE");
        discount.put("value", "10.00");
        discount.put("validFrom", clock.instant().minusSeconds(60).toString());
        discount.put("validUntil", clock.instant().plusSeconds(86_400).toString());
        discount.put("minimumSpend", "0.00");
        discount.put("maximumDiscount", "100.00");
        discount.put("globalUsageLimit", 1);
        discount.put("perCustomerUsageLimit", 1);
        requireStatus(post("/admin/api/v1/discount-codes", discount, ADMIN_EMAIL, ADMIN_PASSWORD, null), 201);

        List<String> emails = createCustomers(2, "discount-race");
        List<String> reservationIds = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            reservationIds.add(requireStatus(post("/api/v1/seat-reservations",
                    Map.of("screeningSeatIds", List.of(scenario.screeningSeatIds().get(index))),
                    emails.get(index), CUSTOMER_PASSWORD, "discount-race-" + index), 201)
                    .body().get("id").asText());
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                int current = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timed out");
                    return post("/api/v1/bookings", Map.of(
                            "reservationId", reservationIds.get(current),
                            "paymentToken", "tok_success",
                            "discountCode", code),
                            emails.get(current), CUSTOMER_PASSWORD, "discount-booking-" + current).status();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Integer> future : futures) statuses.add(future.get(75, TimeUnit.SECONDS));
        }

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 422).hasSize(1);
        assertThat(count("SELECT count(*) FROM discount_redemption"))
                .as("the configured global limit of one is not exceeded").isEqualTo(1);
    }

    @Test
    @Timeout(120)
    void cleanupRacingAReclaimLeavesOneConsistentSeatOwner() throws Exception {
        Scenario scenario = createScenario(1, false);
        String seatId = scenario.screeningSeatIds().getFirst();
        requireStatus(post("/api/v1/seat-reservations", Map.of("screeningSeatIds", List.of(seatId)),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "cleanup-race-first"), 201);

        clock.advance(Duration.ofMinutes(5));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> cleanup = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timed out");
                return reservations.releaseExpiredBatch();
            });
            Future<Integer> reclaim = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timed out");
                return post("/api/v1/seat-reservations", Map.of("screeningSeatIds", List.of(seatId)),
                        CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "cleanup-race-second").status();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            cleanup.get(75, TimeUnit.SECONDS);
            assertThat(reclaim.get(75, TimeUnit.SECONDS)).isEqualTo(201);
        }

        assertThat(count("SELECT count(*) FROM seat_reservation WHERE state = 'ACTIVE'"))
                .as("the expired reservation is gone and only the reclaim survives").isEqualTo(1);
        assertThat(count("SELECT count(*) FROM screening_seat WHERE reservation_id IS NOT NULL")).isEqualTo(1);
        assertThat(seatState(seatId)).isEqualTo("RESERVED");
        assertThat(seatOwner(seatId)).isEqualTo(jdbc.queryForObject(
                "SELECT id::text FROM seat_reservation WHERE state = 'ACTIVE'", String.class));

        // Rerunning cleanup must not disturb the new owner.
        assertThat(reservations.releaseExpiredBatch()).isZero();
        assertThat(seatState(seatId)).isEqualTo("RESERVED");
    }

    @Test
    @Timeout(120)
    void twoWorkersNeverClaimTheSameEventAndAnExpiredLeaseIsReclaimable() throws Exception {
        Scenario scenario = createScenario(3, false);
        for (int index = 0; index < 3; index++) {
            String reservationId = requireStatus(post("/api/v1/seat-reservations",
                    Map.of("screeningSeatIds", List.of(scenario.screeningSeatIds().get(index))),
                    CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "outbox-reservation-" + index), 201)
                    .body().get("id").asText();
            requireStatus(post("/api/v1/bookings", Map.of(
                    "reservationId", reservationId, "paymentToken", "tok_success"),
                    CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "outbox-booking-" + index), 201);
        }
        assertThat(count("SELECT count(*) FROM outbox_event WHERE status = 'PENDING'")).isEqualTo(3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<List<UUID>> claims = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<UUID>>> futures = new ArrayList<>();
            for (int worker = 0; worker < 2; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timed out");
                    return outbox.claimReady().stream().map(OutboxService.DeliveryWork::id).toList();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<List<UUID>> future : futures) claims.add(future.get(75, TimeUnit.SECONDS));
        }

        List<UUID> all = claims.stream().flatMap(List::stream).toList();
        assertThat(all).as("SKIP LOCKED hands each event to one worker only")
                .hasSameSizeAs(Set.copyOf(all));
        assertThat(all).hasSize(3);

        // Neither worker completed its claim, so the leases must expire and become reclaimable.
        assertThat(outbox.claimReady()).isEmpty();
        clock.advance(Duration.ofMinutes(2));
        assertThat(outbox.claimReady()).as("an expired processing lease is recovered").hasSize(3);
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

    private String seatOwner(String seatId) {
        return jdbc.queryForObject("SELECT reservation_id::text FROM screening_seat WHERE id = ?::uuid",
                String.class, seatId);
    }

    private String seatState(String seatId) {
        return jdbc.queryForObject("SELECT state FROM screening_seat WHERE id = ?::uuid", String.class, seatId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
