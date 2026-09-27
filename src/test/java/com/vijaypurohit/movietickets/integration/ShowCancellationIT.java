package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.vijaypurohit.movietickets.booking.application.ScreeningCancellationSweeper;
import com.vijaypurohit.movietickets.notification.LocalNotificationGateway;
import com.vijaypurohit.movietickets.notification.application.OutboxWorker;
import com.vijaypurohit.movietickets.payment.application.RefundWorker;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

/**
 * An administratively cancelled screening must settle the tickets it already sold: the customer did
 * nothing wrong, so the booking's own cutoff rules are bypassed and the full amount is refunded.
 */
class ShowCancellationIT extends ApiIntegrationTest {
    @Autowired private RefundWorker refundWorker;
    @Autowired private OutboxWorker notifications;
    @Autowired private LocalNotificationGateway notificationGateway;
    @Autowired private ScreeningCancellationSweeper sweeper;

    @Test
    void cancellingAScreeningRefundsEveryConfirmedBookingInFullAndReleasesItsSeats() {
        Scenario scenario = createScenario(2, false);
        String bookingId = confirmBooking(scenario, 0, "show-cancel");
        BigDecimal paid = new BigDecimal(jdbc.queryForObject(
                "SELECT amount::text FROM payment WHERE booking_id = ?::uuid", String.class, bookingId));

        // Move to an hour before the show: the booking's own policy would now refund nothing.
        clock.set(scenario.screeningStart().minus(Duration.ofHours(1)));

        requireStatus(delete("/admin/api/v1/screenings/" + scenario.screeningId(),
                ADMIN_EMAIL, ADMIN_PASSWORD), 204);

        assertThat(bookingState(bookingId)).isEqualTo("CANCELLED");
        assertThat(seatStates(scenario.screeningId())).containsOnly("AVAILABLE");

        assertThat(count("SELECT count(*) FROM refund WHERE reason = 'SHOW_CANCELLED'")).isEqualTo(1);
        BigDecimal refunded = new BigDecimal(jdbc.queryForObject(
                "SELECT amount::text FROM refund WHERE reason = 'SHOW_CANCELLED'", String.class));
        assertThat(refunded).as("a cancelled show refunds the full captured amount")
                .isEqualByComparingTo(paid);

        refundWorker.process();
        assertThat(jdbc.queryForObject("SELECT status FROM refund WHERE reason = 'SHOW_CANCELLED'", String.class))
                .isEqualTo("SUCCEEDED");

        notifications.deliver();
        assertThat(notificationGateway.deliveries().stream()
                .filter(value -> value.eventType().equals("SCREENING_CANCELLED"))
                .filter(value -> value.payload().contains(bookingId))
                .toList()).hasSize(1);
    }

    @Test
    void repeatingTheCancellationOrRunningTheSweeperCreatesNoSecondRefund() {
        Scenario scenario = createScenario(1, false);
        confirmBooking(scenario, 0, "show-cancel-repeat");

        requireStatus(delete("/admin/api/v1/screenings/" + scenario.screeningId(),
                ADMIN_EMAIL, ADMIN_PASSWORD), 204);
        requireStatus(delete("/admin/api/v1/screenings/" + scenario.screeningId(),
                ADMIN_EMAIL, ADMIN_PASSWORD), 204);
        assertThat(sweeper.sweep()).as("nothing is left unsettled").isZero();

        assertThat(count("SELECT count(*) FROM refund")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE event_type = 'SCREENING_CANCELLED'")).isEqualTo(1);
    }

    @Test
    void theSweeperFinishesASettlementThatWasInterrupted() {
        Scenario scenario = createScenario(1, false);
        String bookingId = confirmBooking(scenario, 0, "show-cancel-sweep");

        // Simulate a crash between the status change and the booking drain.
        jdbc.update("UPDATE screening SET status = 'CANCELLED' WHERE id = ?::uuid", scenario.screeningId());
        assertThat(bookingState(bookingId)).isEqualTo("CONFIRMED");

        assertThat(sweeper.sweep()).isEqualTo(1);

        assertThat(bookingState(bookingId)).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM refund WHERE reason = 'SHOW_CANCELLED'")).isEqualTo(1);
        assertThat(sweeper.sweep()).as("the sweep is idempotent").isZero();
    }

    @Test
    void aStartedScreeningCannotBeCancelledAndCustomersKeepTheirBookings() {
        Scenario scenario = createScenario(1, false);
        String bookingId = confirmBooking(scenario, 0, "show-cancel-started");

        clock.set(scenario.screeningStart().plus(Duration.ofMinutes(1)));
        ApiResponse response = delete("/admin/api/v1/screenings/" + scenario.screeningId(),
                ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().get("code").asText()).isEqualTo("SCREENING_STARTED");
        assertThat(bookingState(bookingId)).isEqualTo("CONFIRMED");
        assertThat(count("SELECT count(*) FROM refund")).isZero();
    }

    @Test
    void theAdminScreeningListIsPaginatedAndFilterable() {
        Scenario scenario = createScenario(1, false);

        ApiResponse all = requireStatus(get("/admin/api/v1/screenings?size=20", ADMIN_EMAIL, ADMIN_PASSWORD), 200);
        assertThat(all.body().get("items")).isNotEmpty();
        assertThat(all.body().get("size").asInt()).isEqualTo(20);

        ApiResponse filtered = requireStatus(get("/admin/api/v1/screenings?auditoriumId=" + scenario.auditoriumId(),
                ADMIN_EMAIL, ADMIN_PASSWORD), 200);
        assertThat(filtered.body().get("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.body().at("/items/0/id").asText()).isEqualTo(scenario.screeningId());

        assertThat(get("/admin/api/v1/screenings?size=500", ADMIN_EMAIL, ADMIN_PASSWORD).status()).isEqualTo(400);
        assertThat(get("/admin/api/v1/screenings", CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD).status()).isEqualTo(403);
    }

    private String confirmBooking(Scenario scenario, int seatIndex, String keyPrefix) {
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(scenario.screeningSeatIds().get(seatIndex))),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, keyPrefix + "-reservation"), 201);
        ApiResponse booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservation.body().get("id").asText(),
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, keyPrefix + "-booking"), 201);
        assertThat(booking.body().get("state").asText()).isEqualTo("CONFIRMED");
        return booking.body().get("id").asText();
    }

    private String bookingState(String bookingId) {
        return jdbc.queryForObject("SELECT state FROM booking WHERE id = ?::uuid", String.class, bookingId);
    }

    private List<String> seatStates(String screeningId) {
        return jdbc.queryForList("SELECT state FROM screening_seat WHERE screening_id = ?::uuid",
                String.class, screeningId);
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
}
