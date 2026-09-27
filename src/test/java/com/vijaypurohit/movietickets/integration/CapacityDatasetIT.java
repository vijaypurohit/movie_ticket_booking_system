package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import com.vijaypurohit.movietickets.demo.application.LargeDemoDataGenerator;
import com.vijaypurohit.movietickets.reservation.application.SeatReservationService;
import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

class CapacityDatasetIT extends ApiIntegrationTest {
    @Autowired private LargeDemoDataGenerator generator;
    @Autowired private SeatReservationService reservations;

    @Test
    @Timeout(240)
    void generatesIdempotentCapacityDataAndKeepsCoreQueriesBounded() {
        long started = System.nanoTime();
        var report = generator.generate(true);
        Duration firstRun = Duration.ofNanos(System.nanoTime() - started);

        assertThat(report.screeningSeats()).isEqualTo(126_000);
        assertCapacityCounts();
        assertThat(generator.generate(true)).isEqualTo(report);
        assertCapacityCounts();

        UUID cityId = jdbc.queryForObject("select id from city where name = 'Capacity City 1'", UUID.class);
        ApiResponse movies = requireStatus(get("/api/v1/movies?cityId=" + cityId
                + "&date=" + report.anchorDate() + "&page=0&size=5"), 200);
        assertThat(movies.body().get("items")).hasSize(5);
        assertThat(movies.body().get("totalElements").asInt()).isBetween(6, 20);

        ApiResponse firstScreeningPage = requireStatus(get("/api/v1/screenings?cityId=" + cityId
                + "&date=" + report.anchorDate() + "&limit=20"), 200);
        assertThat(firstScreeningPage.body().get("items")).hasSize(20);
        String screeningCursor = firstScreeningPage.body().get("nextCursor").asText();
        ApiResponse secondScreeningPage = requireStatus(get("/api/v1/screenings?cityId=" + cityId
                + "&date=" + report.anchorDate() + "&limit=20&cursor=" + encode(screeningCursor)), 200);
        assertThat(secondScreeningPage.body().get("items")).hasSize(20);
        assertThat(secondScreeningPage.body().get("items").get(0).get("id").asText())
                .isNotEqualTo(firstScreeningPage.body().get("items").get(0).get("id").asText());

        Map<String, Object> available = jdbc.queryForMap("""
                select screening_seat.id as seat_id, screening_seat.screening_id
                from screening_seat
                join screening on screening.id = screening_seat.screening_id
                where screening_seat.state = 'AVAILABLE' and screening.start_time > ?
                order by screening.start_time, screening_seat.id
                limit 1
                """, Timestamp.from(clock.instant()));
        ApiResponse reservation = requireStatus(post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", List.of(available.get("seat_id").toString())),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "capacity-reservation"), 201);
        assertThat(reservation.body().get("state").asText()).isEqualTo("ACTIVE");

        assertThat(pageThroughCapacityHistory()).isEqualTo(10_000);
        assertCleanupBatchBound(available.get("screening_id").toString());
        assertRequiredIndexesExist();

        System.out.printf("Capacity dataset generated in %d ms; this is a local functional observation, not a throughput benchmark.%n",
                firstRun.toMillis());
    }

    private void assertCapacityCounts() {
        assertThat(count("select count(*) from city where name like 'Capacity City %'"))
                .isEqualTo(LargeDemoDataGenerator.CITY_COUNT);
        assertThat(count("select count(*) from theater where name like 'Capacity Theater %'"))
                .isEqualTo(LargeDemoDataGenerator.THEATER_COUNT);
        assertThat(count("select count(*) from auditorium where name like 'Capacity Screen %'"))
                .isEqualTo(LargeDemoDataGenerator.AUDITORIUM_COUNT);
        assertThat(count("select count(*) from seat where id in (select seat.id from seat join auditorium on auditorium.id = seat.auditorium_id where auditorium.name like 'Capacity Screen %')"))
                .isEqualTo(LargeDemoDataGenerator.AUDITORIUM_COUNT * LargeDemoDataGenerator.SEATS_PER_AUDITORIUM);
        assertThat(count("select count(*) from movie where title like 'Capacity Movie %'"))
                .isEqualTo(LargeDemoDataGenerator.MOVIE_COUNT);
        assertThat(count("select count(*) from screening where pricing_plan_id = '" + capacityPricingId() + "'"))
                .isEqualTo(LargeDemoDataGenerator.SCREENING_COUNT);
        assertThat(count("select count(*) from screening_seat where screening_id in (select id from screening where pricing_plan_id = '"
                + capacityPricingId() + "')")).isEqualTo(LargeDemoDataGenerator.SCREENING_SEAT_COUNT);
        assertThat(count("select count(*) from app_user where email like 'capacity.customer.%@movietickets.local'"))
                .isEqualTo(LargeDemoDataGenerator.CUSTOMER_COUNT - 2);
        assertThat(count("select count(*) from app_user where role = 'CUSTOMER'"))
                .isEqualTo(LargeDemoDataGenerator.CUSTOMER_COUNT);
        assertThat(count("select count(*) from booking where reference like 'CAP%'"))
                .isEqualTo(LargeDemoDataGenerator.BOOKING_COUNT);
    }

    private int pageThroughCapacityHistory() {
        int total = 0;
        String cursor = null;
        do {
            String path = "/api/v1/bookings?limit=100" + (cursor == null ? "" : "&cursor=" + encode(cursor));
            ApiResponse page = requireStatus(get(path, "capacity.customer.0000@movietickets.local",
                    LargeDemoDataGenerator.CUSTOMER_PASSWORD), 200);
            total += page.body().get("items").size();
            cursor = page.body().get("nextCursor").isNull() ? null : page.body().get("nextCursor").asText();
        } while (cursor != null);
        return total;
    }

    private void assertCleanupBatchBound(String screeningId) {
        UUID customerId = jdbc.queryForObject(
                "select id from app_user where email = 'capacity.customer.0001@movietickets.local'", UUID.class);
        Instant expired = clock.instant().minusSeconds(1);
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < 101; index++) values.add(index);
        jdbc.batchUpdate("""
                insert into seat_reservation (id, customer_id, screening_id, state, expires_at,
                    idempotency_key, request_fingerprint, created_at, updated_at, version)
                values (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, 0)
                """, values, 101, (statement, index) -> {
            statement.setObject(1, UUID.nameUUIDFromBytes(("capacity-expired-" + index).getBytes(StandardCharsets.UTF_8)));
            statement.setObject(2, customerId); statement.setObject(3, UUID.fromString(screeningId));
            statement.setTimestamp(4, Timestamp.from(expired)); statement.setString(5, "capacity-expired-" + index);
            statement.setString(6, "f".repeat(64)); statement.setTimestamp(7, Timestamp.from(expired));
            statement.setTimestamp(8, Timestamp.from(expired));
        });

        assertThat(reservations.releaseExpiredBatch()).isEqualTo(100);
        assertThat(reservations.releaseExpiredBatch()).isEqualTo(1);
        assertThat(reservations.releaseExpiredBatch()).isZero();
    }

    private void assertRequiredIndexesExist() {
        List<String> indexes = jdbc.queryForList("""
                select indexname from pg_indexes where schemaname = 'public' and indexname in
                    ('idx_screening_seat_screening_state', 'idx_booking_customer_history',
                     'idx_reservation_active_expiry', 'idx_screening_movie_time')
                """, String.class);
        assertThat(indexes).containsExactlyInAnyOrder("idx_screening_seat_screening_state",
                "idx_booking_customer_history", "idx_reservation_active_expiry", "idx_screening_movie_time");
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
    private String capacityPricingId() {
        return jdbc.queryForObject("select id::text from pricing_plan where name = 'Capacity Pricing'", String.class);
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
