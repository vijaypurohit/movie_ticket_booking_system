package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.vijaypurohit.movietickets.notification.LocalNotificationGateway;
import com.vijaypurohit.movietickets.notification.application.OutboxWorker;
import com.vijaypurohit.movietickets.payment.application.RefundWorker;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;
import com.vijaypurohit.movietickets.support.ScriptedPaymentGateway;
import com.vijaypurohit.movietickets.support.TestGatewayConfiguration;

/**
 * Covers the refund worker itself, including the retryable and terminal provider failures that the
 * deterministic local adapter cannot produce on its own.
 */
@Import(TestGatewayConfiguration.class)
class RefundWorkerIT extends ApiIntegrationTest {
    @Autowired private RefundWorker refundWorker;
    @Autowired private OutboxWorker notifications;
    @Autowired private LocalNotificationGateway notificationGateway;
    @Autowired private ScriptedPaymentGateway gateway;

    @org.junit.jupiter.api.BeforeEach
    void resetGateway() { gateway.reset(); }

    @Test
    void pendingRefundIsProcessedOnceAndAnnouncedThroughTheOutbox() {
        String refundId = cancelledBookingRefundId("worker-success");

        refundWorker.process();

        assertThat(refundStatus(refundId)).isEqualTo("SUCCEEDED");
        assertThat(gateway.refundCalls()).isEqualTo(1);

        // A second pass must not re-invoke the provider for a settled refund.
        refundWorker.process();
        assertThat(gateway.refundCalls()).isEqualTo(1);

        notifications.deliver();
        assertThat(deliveries("REFUND_SUCCEEDED", refundId)).hasSize(1);
    }

    @Test
    void retryableProviderFailureIsRetriedOnALaterBoundedAttemptAndThenSucceeds() {
        String refundId = cancelledBookingRefundId("worker-retryable");
        gateway.scriptRefunds(ScriptedPaymentGateway.retryableFailure());

        refundWorker.process();
        assertThat(refundStatus(refundId)).isEqualTo("PENDING");
        assertThat(attemptCount(refundId)).isEqualTo(1);

        // The retry is scheduled, so an immediate pass must not pick the row up again.
        refundWorker.process();
        assertThat(gateway.refundCalls()).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        refundWorker.process();
        assertThat(refundStatus(refundId)).isEqualTo("SUCCEEDED");
        assertThat(attemptCount(refundId)).isEqualTo(2);
    }

    @Test
    void terminalProviderFailureStopsAtTheConfiguredAttemptLimit() {
        String refundId = cancelledBookingRefundId("worker-terminal");
        gateway.scriptRefunds(ScriptedPaymentGateway.terminalFailure());

        refundWorker.process();

        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
        assertThat(gateway.refundCalls()).isEqualTo(1);

        clock.advance(Duration.ofHours(1));
        refundWorker.process();
        assertThat(gateway.refundCalls()).as("a terminal failure is not retried").isEqualTo(1);

        notifications.deliver();
        assertThat(deliveries("REFUND_FAILED", refundId)).hasSize(1);
    }

    @Test
    void repeatedRetryableFailuresStopOnceTheAttemptLimitIsExhausted() {
        String refundId = cancelledBookingRefundId("worker-exhausted");
        gateway.scriptRefunds(ScriptedPaymentGateway.retryableFailure(),
                ScriptedPaymentGateway.retryableFailure(),
                ScriptedPaymentGateway.retryableFailure(),
                ScriptedPaymentGateway.retryableFailure());

        for (int attempt = 0; attempt < 5; attempt++) {
            refundWorker.process();
            clock.advance(Duration.ofMinutes(1));
        }

        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
        assertThat(attemptCount(refundId))
                .as("bounded by app.refund.max-attempts")
                .isEqualTo(3);
    }

    /** Books and cancels a seat, returning the identifier of the resulting pending refund. */
    private String cancelledBookingRefundId(String keyPrefix) {
        Scenario scenario = createScenario(1, false);
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", scenario.screeningSeatIds()),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, keyPrefix + "-reservation"), 201);
        ApiResponse booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservation.body().get("id").asText(),
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, keyPrefix + "-booking"), 201);
        ApiResponse cancellation = requireStatus(post(
                "/api/v1/bookings/" + booking.body().get("id").asText() + "/cancellations", null,
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, keyPrefix + "-cancel"), 200);
        return cancellation.body().at("/refund/id").asText();
    }

    private String refundStatus(String refundId) {
        return jdbc.queryForObject("SELECT status FROM refund WHERE id = ?::uuid", String.class, refundId);
    }

    private int attemptCount(String refundId) {
        return jdbc.queryForObject("SELECT attempt_count FROM refund WHERE id = ?::uuid", Integer.class, refundId);
    }

    private List<LocalNotificationGateway.Delivery> deliveries(String eventType, String aggregateId) {
        return notificationGateway.deliveries().stream()
                .filter(value -> value.eventType().equals(eventType))
                .filter(value -> value.payload().contains(aggregateId))
                .toList();
    }
}
