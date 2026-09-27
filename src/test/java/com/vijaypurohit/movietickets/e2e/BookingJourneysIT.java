package com.vijaypurohit.movietickets.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.vijaypurohit.movietickets.notification.LocalNotificationGateway;
import com.vijaypurohit.movietickets.notification.application.OutboxWorker;
import com.vijaypurohit.movietickets.notification.application.ReminderProducer;
import com.vijaypurohit.movietickets.payment.application.RefundWorker;
import com.vijaypurohit.movietickets.reservation.application.SeatReservationService;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

class BookingJourneysIT extends ApiIntegrationTest {
    @Autowired private OutboxWorker notifications;
    @Autowired private RefundWorker refundWorker;
    @Autowired private ReminderProducer reminders;
    @Autowired private LocalNotificationGateway notificationGateway;
    @Autowired private SeatReservationService reservations;

    @Test
    void adminSetupBrowseDiscountedBookingHistoryCancellationRefundAndSeatReuse() {
        Scenario scenario = createScenario(2, true);

        ApiResponse movies = requireStatus(get("/api/v1/movies?cityId=" + scenario.cityId()
                + "&date=" + scenario.screeningDate()), 200);
        ApiResponse screenings = requireStatus(get("/api/v1/screenings?cityId=" + scenario.cityId()
                + "&movieId=" + scenario.movieId() + "&date=" + scenario.screeningDate()), 200);
        assertThat(movies.body().get("items").size()).isEqualTo(1);
        assertThat(screenings.body().get("items").size()).isEqualTo(1);

        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", scenario.screeningSeatIds()),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "reservation-success"), 201);
        ApiResponse booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservation.body().get("id").asText(),
                "discountCode", scenario.discountCode(),
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "booking-success"), 201);

        String bookingId = booking.body().get("id").asText();
        assertThat(booking.body().get("state").asText()).isEqualTo("CONFIRMED");
        assertThat(booking.body().get("paymentStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(booking.body().get("subtotal").decimalValue()).isEqualByComparingTo("750.00");
        assertThat(booking.body().get("discountAmount").decimalValue()).isEqualByComparingTo("75.00");
        assertThat(booking.body().get("totalAmount").decimalValue()).isEqualByComparingTo("675.00");
        assertThat(booking.body().get("currency").asText()).isEqualTo("INR");

        ApiResponse history = requireStatus(get("/api/v1/bookings?limit=1",
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD), 200);
        assertThat(history.body().get("items").get(0).get("id").asText()).isEqualTo(bookingId);
        notifications.deliver();
        assertThat(deliveries("BOOKING_CONFIRMED", bookingId)).hasSize(1);

        ApiResponse cancellation = requireStatus(post("/api/v1/bookings/" + bookingId + "/cancellations",
                null, CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "cancellation-success"), 200);
        String refundId = cancellation.body().at("/refund/id").asText();
        assertThat(cancellation.body().get("bookingState").asText()).isEqualTo("CANCELLED");
        assertThat(cancellation.body().at("/refund/amount").decimalValue()).isEqualByComparingTo("675.00");

        refundWorker.process();
        notifications.deliver();
        ApiResponse refund = requireStatus(get("/api/v1/refunds/" + refundId,
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD), 200);
        assertThat(refund.body().get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(deliveries("BOOKING_CANCELLED", bookingId)).hasSize(1);
        assertThat(notificationGateway.deliveries().stream()
                .filter(value -> value.eventType().equals("REFUND_SUCCEEDED"))
                .filter(value -> value.payload().contains(refundId))).hasSize(1);

        ApiResponse reused = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(scenario.screeningSeatIds().getFirst())),
                CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "reservation-after-cancellation"), 201);
        assertThat(reused.body().get("state").asText()).isEqualTo("ACTIVE");

        ApiResponse repeatedCancellation = requireStatus(post(
                "/api/v1/bookings/" + bookingId + "/cancellations", null,
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "cancellation-success"), 200);
        assertThat(repeatedCancellation.body().at("/refund/id").asText()).isEqualTo(refundId);
    }

    @Test
    void reservationExpiresAtTheExactBoundaryAndIsReclaimedWithoutCleanup() {
        Scenario scenario = createScenario(1, false);
        String seatId = scenario.screeningSeatIds().getFirst();
        ApiResponse first = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(seatId)),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "expiring-reservation"), 201);

        clock.advance(Duration.ofMinutes(4));
        ApiResponse second = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(seatId)),
                CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "reclaimed-reservation"), 201);
        assertThat(second.body().get("id").asText()).isNotEqualTo(first.body().get("id").asText());

        ApiResponse expiredCheckout = post("/api/v1/bookings", Map.of(
                "reservationId", first.body().get("id").asText(),
                "paymentToken", "tok_success"), CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "expired-booking");
        assertThat(expiredCheckout.status()).isEqualTo(409);
        assertThat(expiredCheckout.body().get("code").asText()).isEqualTo("RESERVATION_NOT_ACTIVE");
        assertThat(reservations.releaseExpiredBatch()).isZero();
    }

    @Test
    void declinedPaymentReleasesSeatAndWritesNoConfirmationEvent() {
        Scenario scenario = createScenario(1, false);
        String seatId = scenario.screeningSeatIds().getFirst();
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(seatId)),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "decline-reservation"), 201);
        ApiResponse booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservation.body().get("id").asText(),
                "paymentToken", "tok_decline"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "declined-booking"), 201);

        assertThat(booking.body().get("state").asText()).isEqualTo("FAILED");
        assertThat(booking.body().get("paymentStatus").asText()).isEqualTo("DECLINED");
        ApiResponse seats = requireStatus(get("/api/v1/screenings/" + scenario.screeningId() + "/seats"), 200);
        assertThat(seats.body().get(0).get("state").asText()).isEqualTo("AVAILABLE");
        notifications.deliver();
        assertThat(deliveries("BOOKING_CONFIRMED", booking.body().get("id").asText())).isEmpty();
    }

    @Test
    void reminderProductionAndDeliveryAreIdempotent() {
        Scenario scenario = createScenario(1, false);
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", scenario.screeningSeatIds()),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "reminder-reservation"), 201);
        ApiResponse booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservation.body().get("id").asText(),
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "reminder-booking"), 201);
        String bookingId = booking.body().get("id").asText();

        clock.set(scenario.screeningStart().minus(Duration.ofHours(24)));
        assertThat(reminders.produce()).isEqualTo(1);
        assertThat(reminders.produce()).isZero();
        notifications.deliver();
        notifications.deliver();

        assertThat(deliveries("BOOKING_REMINDER", bookingId)).hasSize(1);
    }

    private List<LocalNotificationGateway.Delivery> deliveries(String eventType, String aggregateId) {
        return notificationGateway.deliveries().stream()
                .filter(value -> value.eventType().equals(eventType))
                .filter(value -> value.payload().contains(aggregateId))
                .toList();
    }
}
