package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import com.vijaypurohit.movietickets.demo.application.LocalDemoDataSeeder;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

import tools.jackson.databind.JsonNode;

/**
 * Covers the dataset the demo profile seeds, which is what the README walkthrough and the
 * recorded demo rely on. The seeder is idempotent and uses deterministic identifiers, so
 * these expectations hold on a freshly migrated database and after any restart.
 */
class DemoDatasetIT extends ApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired private LocalDemoDataSeeder seeder;

    /**
     * The shared base class truncates every table before each test, which also removes what
     * the seeder wrote at startup. Re-running it here is exactly what a fresh boot does, and
     * it proves the seeder is idempotent and clock-driven rather than fixed to a build date.
     */
    @BeforeEach
    void seedDemoDataset() {
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());
    }

    private LocalDate firstDemoDay() {
        return LocalDate.now(clock.withZone(ZONE)).plusDays(1);
    }

    @Test
    void theDemoProfileSeedsOneDiscoverableCityTheaterAndAWeekOfScreenings() {
        JsonNode cities = requireStatus(get("/api/v1/cities"), 200).body();
        JsonNode city = named(cities.get("items"), "Demo Pune");
        assertThat(city).as("seeded demo city").isNotNull();
        assertThat(city.get("country").asString()).isEqualTo("India");
        assertThat(city.get("timeZone").asString()).isEqualTo(ZONE.getId());

        String cityId = city.get("id").asString();
        JsonNode theaters = requireStatus(get("/api/v1/theaters?cityId=" + cityId), 200).body();
        assertThat(named(theaters.get("items"), "Demo Cinema")).as("seeded demo theater").isNotNull();

        // An 18:30 IST screening on each of the seven days from tomorrow.
        LocalDate tomorrow = firstDemoDay();
        for (int day = 0; day < 7; day++) {
            LocalDate date = tomorrow.plusDays(day);
            JsonNode screenings = requireStatus(
                    get("/api/v1/screenings?cityId=" + cityId + "&date=" + date), 200).body();
            assertThat(screenings.get("items")).as("screenings on %s", date).isNotEmpty();
        }

        String screeningId = firstDemoScreening(cityId, tomorrow);
        JsonNode seats = requireStatus(get("/api/v1/screenings/" + screeningId + "/seats"), 200).body();
        assertThat(seats).as("four seeded seats").hasSize(4);
        assertThat(seats).allSatisfy(seat ->
                assertThat(seat.get("state").asString()).isEqualTo("AVAILABLE"));
    }

    @Test
    void theSeededPercentageDiscountAppliesAtCheckout() {
        String screeningId = firstDemoScreening();
        String seatId = firstAvailableSeat(screeningId);
        String reservationId = reserve(seatId, "demo-discount-reserve");

        JsonNode booking = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", reservationId,
                "discountCode", "DEMO10",
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "demo-discount-book"), 201).body();

        assertThat(booking.get("state").asString()).isEqualTo("CONFIRMED");
        assertThat(booking.get("discountAmount").decimalValue())
                .as("DEMO10 takes 10 percent off the subtotal")
                .isEqualByComparingTo(booking.get("subtotal").decimalValue()
                        .multiply(new java.math.BigDecimal("0.10")));
        assertThat(booking.get("totalAmount").decimalValue())
                .isEqualByComparingTo(booking.get("subtotal").decimalValue()
                        .subtract(booking.get("discountAmount").decimalValue()));
    }

    @Test
    void theSeededSingleUseDiscountIsRejectedOnASecondRedemption() {
        String screeningId = firstDemoScreening();

        String firstSeat = firstAvailableSeat(screeningId);
        String firstReservation = reserve(firstSeat, "demo-single-use-reserve-1");
        JsonNode first = requireStatus(post("/api/v1/bookings", Map.of(
                "reservationId", firstReservation,
                "discountCode", "DEMO50",
                "paymentToken", "tok_success"),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "demo-single-use-book-1"), 201).body();
        assertThat(first.get("discountAmount").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("50.00"));

        String secondSeat = firstAvailableSeat(screeningId);
        String secondReservation = reserve(secondSeat, "demo-single-use-reserve-2");
        var rejected = post("/api/v1/bookings", Map.of(
                "reservationId", secondReservation,
                "discountCode", "DEMO50",
                "paymentToken", "tok_success"),
                CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "demo-single-use-book-2");

        assertThat(rejected.status()).isEqualTo(422);
        assertThat(rejected.body().get("code").asString()).isEqualTo("DISCOUNT_LIMIT_REACHED");
    }

    private String reserve(String screeningSeatId, String idempotencyKey) {
        return requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(screeningSeatId)),
                idempotencyKey.endsWith("-2") ? CUSTOMER_TWO_EMAIL : CUSTOMER_ONE_EMAIL,
                CUSTOMER_PASSWORD, idempotencyKey), 201).body().get("id").asString();
    }

    private String firstDemoScreening() {
        JsonNode cities = requireStatus(get("/api/v1/cities"), 200).body();
        String cityId = named(cities.get("items"), "Demo Pune").get("id").asString();
        return firstDemoScreening(cityId, firstDemoDay());
    }

    private String firstDemoScreening(String cityId, LocalDate date) {
        JsonNode screenings = requireStatus(
                get("/api/v1/screenings?cityId=" + cityId + "&date=" + date), 200).body();
        return screenings.get("items").get(0).get("id").asString();
    }

    private String firstAvailableSeat(String screeningId) {
        JsonNode seats = requireStatus(get("/api/v1/screenings/" + screeningId + "/seats"), 200).body();
        for (JsonNode seat : seats) {
            if ("AVAILABLE".equals(seat.get("state").asString())) {
                return seat.get("screeningSeatId").asString();
            }
        }
        throw new IllegalStateException("no available seat on screening " + UUID.fromString(screeningId));
    }

    private static JsonNode named(JsonNode items, String name) {
        for (JsonNode item : items) {
            if (name.equals(item.get("name").asString())) return item;
        }
        return null;
    }
}
