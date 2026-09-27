package com.vijaypurohit.movietickets.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.booking.enabled=true",
        "app.browse.enabled=true",
        "app.catalog.enabled=true",
        "app.demo.enabled=true",
        "app.demo.generator-enabled=true",
        "app.demo.anchor-date=2026-10-05",
        "app.identity.enabled=true",
        "app.notification.enabled=true",
        "app.persistence.auditing.enabled=true",
        "app.pricing.enabled=true",
        "app.reservation.enabled=true",
        "app.screening.enabled=true",
        "app.booking.cleanup-delay=PT24H",
        "app.notification.worker-delay=PT24H",
        "app.notification.reminder-scan-delay=PT24H",
        "app.refund.worker-delay=PT24H",
        "spring.datasource.hikari.maximum-pool-size=30",
        "spring.task.scheduling.enabled=false"
})
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {
    protected static final String ADMIN_EMAIL = "admin@movietickets.local";
    protected static final String CUSTOMER_ONE_EMAIL = "customer1@movietickets.local";
    protected static final String CUSTOMER_TWO_EMAIL = "customer2@movietickets.local";
    protected static final String ADMIN_PASSWORD = "Admin@123";
    protected static final String CUSTOMER_PASSWORD = "Customer@123";

    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @LocalServerPort private int port;

    protected ApiResponse get(String path) {
        return exchange("GET", path, null, null, null);
    }

    protected ApiResponse get(String path, String email, String password) {
        return exchange("GET", path, null, email, password);
    }

    protected ApiResponse post(String path, Object body, String email, String password, String idempotencyKey) {
        return exchange("POST", path, body, email, password, idempotencyKey);
    }

    protected ApiResponse delete(String path, String email, String password) {
        return exchange("DELETE", path, null, email, password);
    }

    protected ApiResponse exchange(String method, String path, Object body, String email, String password) {
        return exchange(method, path, body, email, password, null);
    }

    protected ApiResponse exchange(String method, String path, Object body, String email, String password,
            String idempotencyKey) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json");
            if (email != null) {
                String credentials = Base64.getEncoder().encodeToString(
                        (email + ':' + password).getBytes(StandardCharsets.UTF_8));
                request.header("Authorization", "Basic " + credentials);
            }
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
            return new ApiResponse(response.statusCode(), response.headers().firstValue("content-type").orElse(""),
                    response.headers().firstValue("x-request-id").orElse(null), json);
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request interrupted", exception);
        }
    }

    protected Scenario createScenario(int seatCount, boolean createDiscount) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode city = requireStatus(post("/admin/api/v1/cities", Map.of(
                "name", "Pune " + suffix,
                "country", "India",
                "timeZone", "Asia/Kolkata"), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String cityId = city.get("id").asText();

        JsonNode theater = requireStatus(post("/admin/api/v1/theaters", Map.of(
                "cityId", cityId,
                "name", "Cinema " + suffix,
                "address", "Test address"), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String theaterId = theater.get("id").asText();

        JsonNode auditorium = requireStatus(post("/admin/api/v1/theaters/" + theaterId + "/auditoriums",
                Map.of("name", "Screen " + suffix), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String auditoriumId = auditorium.get("id").asText();
        List<String> physicalSeatIds = new ArrayList<>();
        for (int index = 1; index <= seatCount; index++) {
            JsonNode seat = requireStatus(post("/admin/api/v1/auditoriums/" + auditoriumId + "/seats", Map.of(
                    "rowLabel", index % 2 == 0 ? "B" : "A",
                    "seatNumber", index,
                    "category", index % 2 == 0 ? "PREMIUM" : "REGULAR"),
                    ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
            physicalSeatIds.add(seat.get("id").asText());
        }

        JsonNode movie = requireStatus(post("/admin/api/v1/movies", Map.of(
                "title", "Integration Movie " + suffix,
                "durationMinutes", 120,
                "language", "English"), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String movieId = movie.get("id").asText();

        JsonNode pricing = requireStatus(post("/admin/api/v1/pricing-plans", Map.of(
                "name", "Pricing " + suffix,
                "regularPrice", "250.00",
                "premiumPrice", "400.00",
                "weekendAdjustment", "50.00"), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String pricingId = pricing.get("id").asText();

        List<Map<String, Object>> rules = List.of(
                Map.of("cutoffMinutes", 1440, "refundPercentage", "100.00"),
                Map.of("cutoffMinutes", 120, "refundPercentage", "50.00"),
                Map.of("cutoffMinutes", 0, "refundPercentage", "0.00"));
        JsonNode policy = requireStatus(post("/admin/api/v1/refund-policies", Map.of(
                "name", "Refund " + suffix,
                "rules", rules), ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String policyId = policy.get("id").asText();

        String discountCode = null;
        if (createDiscount) {
            discountCode = "SAVE" + suffix.toUpperCase();
            Map<String, Object> discount = new LinkedHashMap<>();
            discount.put("code", discountCode);
            discount.put("type", "PERCENTAGE");
            discount.put("value", "10.00");
            discount.put("validFrom", clock.instant().minusSeconds(60).toString());
            discount.put("validUntil", clock.instant().plusSeconds(30L * 24 * 60 * 60).toString());
            discount.put("minimumSpend", "100.00");
            discount.put("maximumDiscount", "100.00");
            discount.put("globalUsageLimit", 10);
            discount.put("perCustomerUsageLimit", 1);
            requireStatus(post("/admin/api/v1/discount-codes", discount,
                    ADMIN_EMAIL, ADMIN_PASSWORD, null), 201);
        }

        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate saturday = clock.instant().atZone(zone).toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        ZonedDateTime localStart = saturday.atTime(LocalTime.of(18, 30)).atZone(zone);
        JsonNode screening = requireStatus(post("/admin/api/v1/screenings", Map.of(
                "movieId", movieId,
                "auditoriumId", auditoriumId,
                "pricingPlanId", pricingId,
                "refundPolicyId", policyId,
                "startTime", localStart.toInstant().toString(),
                "endTime", localStart.plusHours(2).toInstant().toString()),
                ADMIN_EMAIL, ADMIN_PASSWORD, null), 201).body();
        String screeningId = screening.get("id").asText();

        JsonNode availableSeats = requireStatus(get("/api/v1/screenings/" + screeningId + "/seats"), 200).body();
        List<String> screeningSeatIds = new ArrayList<>();
        availableSeats.forEach(value -> screeningSeatIds.add(value.get("screeningSeatId").asText()));
        return new Scenario(cityId, theaterId, auditoriumId, movieId, screeningId, saturday,
                localStart.toInstant(),
                List.copyOf(screeningSeatIds), discountCode);
    }

    protected ApiResponse requireStatus(ApiResponse response, int expectedStatus) {
        if (response.status() != expectedStatus) {
            throw new AssertionError("Expected HTTP " + expectedStatus + " but received " + response.status()
                    + ": " + response.body());
        }
        return response;
    }

    protected record ApiResponse(int status, String contentType, String requestId, JsonNode body) { }
    protected record Scenario(String cityId, String theaterId, String auditoriumId, String movieId,
            String screeningId, LocalDate screeningDate, java.time.Instant screeningStart,
            List<String> screeningSeatIds, String discountCode) { }
}
