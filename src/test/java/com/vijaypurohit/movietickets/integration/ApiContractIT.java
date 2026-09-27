package com.vijaypurohit.movietickets.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import com.vijaypurohit.movietickets.support.ApiIntegrationTest;

class ApiContractIT extends ApiIntegrationTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");
    private static final Map<String, Set<String>> EXPECTED_OPERATIONS = expectedOperations();

    @Autowired private Environment environment;

    @Test
    void publicProtectedAndAdminRoutesEnforceTheDocumentedRoles() {
        ApiResponse publicBrowse = get("/api/v1/cities");
        ApiResponse anonymousAdmin = get("/admin/api/v1/cities");
        ApiResponse customerAdmin = get("/admin/api/v1/cities", CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD);
        ApiResponse adminCustomerRoute = get("/api/v1/bookings", ADMIN_EMAIL, ADMIN_PASSWORD);

        assertThat(publicBrowse.status()).isEqualTo(200);
        assertProblem(anonymousAdmin, 401, "AUTHENTICATION_REQUIRED");
        assertProblem(customerAdmin, 403, "ACCESS_DENIED");
        assertProblem(adminCustomerRoute, 403, "ACCESS_DENIED");
    }

    @Test
    void validationAndMalformedContractsReturnSafeProblemDetails() {
        ApiResponse badPage = get("/api/v1/cities?size=0");
        ApiResponse unknownField = post("/admin/api/v1/cities", Map.of(
                "name", "Pune", "country", "India", "timeZone", "Asia/Kolkata",
                "systemManaged", true), ADMIN_EMAIL, ADMIN_PASSWORD, null);
        ApiResponse missingIdempotency = post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", java.util.List.of(UUID.randomUUID().toString())),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, null);

        assertProblem(badPage, 400, "INVALID_PAGE");
        assertProblem(unknownField, 400, "INVALID_REQUEST");
        assertProblem(missingIdempotency, 400, "INVALID_REQUEST");
        assertThat(unknownField.body().toString()).doesNotContainIgnoringCase("stacktrace", "sql", "password");
    }

    @Test
    void rawSqlAndJdbcErrorLoggingRemainDisabled() {
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class, false)).isFalse();
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.error")).isEqualTo("OFF");
    }

    @Test
    void ownershipIsConcealedAndConflictsUseTheExpectedStatus() {
        Scenario scenario = createScenario(1, false);
        String seatId = scenario.screeningSeatIds().getFirst();
        ApiResponse reservation = post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", java.util.List.of(seatId)),
                CUSTOMER_ONE_EMAIL, CUSTOMER_PASSWORD, "reservation-owner");
        requireStatus(reservation, 201);

        ApiResponse wrongOwner = get("/api/v1/seat-reservations/" + reservation.body().get("id").asText(),
                CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD);
        ApiResponse duplicateSeats = post("/api/v1/seat-reservations",
                Map.of("screeningSeatIds", java.util.List.of(seatId, seatId)),
                CUSTOMER_TWO_EMAIL, CUSTOMER_PASSWORD, "duplicate-seat-request");

        assertProblem(wrongOwner, 404, "RESOURCE_NOT_FOUND");
        assertProblem(duplicateSeats, 409, "DUPLICATE_SEAT");
    }

    @Test
    void openApiContainsOnlyTheImplementedContractWithSecurityParametersExamplesAndErrors() {
        ApiResponse response = requireStatus(get("/v3/api-docs"), 200);
        var paths = response.body().get("paths");

        assertThat(actualOperations(paths)).isEqualTo(EXPECTED_OPERATIONS);
        assertThat(response.body().at("/components/schemas/ProblemDetails").isObject()).isTrue();
        assertThat(response.body().at("/components/securitySchemes/basicAuth/scheme").asText()).isEqualTo("basic");
        assertThat(response.body().at("/components/responses/BadRequest/content/application~1problem+json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ProblemDetails");

        EXPECTED_OPERATIONS.forEach((path, methods) -> methods.forEach(method -> {
            var operation = paths.get(path).get(method);
            assertThat(operation.get("operationId").asText()).isNotBlank();
            assertThat(operation.get("tags").isArray()).isTrue();
            assertThat(operation.get("responses").has("400")).isTrue();
            assertThat(operation.get("responses").has("500")).isTrue();
            String successStatus = successStatus(path, method);
            assertThat(operation.get("responses").has(successStatus)).isTrue();
            if (!method.equals("delete")) {
                assertThat(operation.at("/responses/" + successStatus + "/content/application~1json/schema").isObject())
                        .as("%s %s success response schema", method.toUpperCase(), path)
                        .isTrue();
            }
            if (isProtected(path)) {
                assertThat(operation.get("security").isArray()).isTrue();
                assertThat(operation.get("responses").has("401")).isTrue();
                assertThat(operation.get("responses").has("403")).isTrue();
            } else {
                assertThat(operation.has("security")).isFalse();
            }
            if (path.contains("{")) assertThat(operation.get("responses").has("404")).isTrue();
            if (!method.equals("get")) {
                assertThat(operation.get("responses").has("409")).isTrue();
                assertThat(operation.get("responses").has("422")).isTrue();
            }
        }));

        assertParameterReferences(paths, "/api/v1/cities", "get", "Page", "PageSize");
        assertParameterReferences(paths, "/api/v1/screenings", "get", "Cursor", "CursorLimit");
        assertParameterReferences(paths, "/api/v1/bookings", "get", "Cursor", "CursorLimit");
        assertParameterReferences(paths, "/api/v1/seat-reservations", "post", "IdempotencyKey");
        assertParameterReferences(paths, "/api/v1/bookings", "post", "IdempotencyKey");
        assertParameterReferences(paths, "/api/v1/bookings/{id}/cancellations", "post", "IdempotencyKey");

        assertThat(paths.get("/api/v1/seat-reservations")
                .at("/post/requestBody/content/application~1json/example").isObject())
                .isTrue();
        assertThat(paths.get("/api/v1/bookings")
                .at("/post/requestBody/content/application~1json/example/paymentToken").asText())
                .isEqualTo("tok_success");
        assertThat(paths.get("/admin/api/v1/screenings")
                .at("/post/requestBody/content/application~1json/example").isObject())
                .isTrue();
    }

    private Map<String, Set<String>> actualOperations(tools.jackson.databind.JsonNode paths) {
        Map<String, Set<String>> actual = new TreeMap<>();
        paths.properties().forEach(entry -> {
            Set<String> methods = new java.util.TreeSet<>();
            entry.getValue().propertyNames().forEach(name -> {
                if (HTTP_METHODS.contains(name)) methods.add(name);
            });
            actual.put(entry.getKey(), methods);
        });
        return actual;
    }

    private void assertParameterReferences(tools.jackson.databind.JsonNode paths,
            String path, String method, String... componentNames) {
        var parameters = paths.get(path).get(method).get("parameters");
        Set<String> references = new java.util.HashSet<>();
        parameters.forEach(parameter -> references.add(parameter.path("$ref").asText()));
        for (String componentName : componentNames) {
            assertThat(references).contains("#/components/parameters/" + componentName);
        }
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/admin/api/v1/")
                || path.startsWith("/api/v1/seat-reservations")
                || path.startsWith("/api/v1/bookings")
                || path.startsWith("/api/v1/refunds");
    }

    private static String successStatus(String path, String method) {
        if (method.equals("delete")) return "204";
        if (method.equals("post") && !path.endsWith("/cancellations")) return "201";
        return "200";
    }

    private static Map<String, Set<String>> expectedOperations() {
        Map<String, Set<String>> operations = new LinkedHashMap<>();
        operations.put("/api/v1/cities", Set.of("get"));
        operations.put("/api/v1/theaters", Set.of("get"));
        operations.put("/api/v1/movies", Set.of("get"));
        operations.put("/api/v1/movies/{id}", Set.of("get"));
        operations.put("/api/v1/screenings", Set.of("get"));
        operations.put("/api/v1/screenings/{id}", Set.of("get"));
        operations.put("/api/v1/screenings/{id}/seats", Set.of("get"));
        operations.put("/api/v1/seat-reservations", Set.of("post"));
        operations.put("/api/v1/seat-reservations/{id}", Set.of("get", "delete"));
        operations.put("/api/v1/bookings", Set.of("get", "post"));
        operations.put("/api/v1/bookings/{id}", Set.of("get"));
        operations.put("/api/v1/bookings/{id}/cancellations", Set.of("post"));
        operations.put("/api/v1/refunds/{id}", Set.of("get"));
        operations.put("/admin/api/v1/cities", Set.of("get", "post"));
        operations.put("/admin/api/v1/cities/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/theaters", Set.of("get", "post"));
        operations.put("/admin/api/v1/theaters/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/theaters/{theaterId}/auditoriums", Set.of("get", "post"));
        operations.put("/admin/api/v1/auditoriums/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/auditoriums/{auditoriumId}/seats", Set.of("get", "post"));
        operations.put("/admin/api/v1/seats/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/movies", Set.of("get", "post"));
        operations.put("/admin/api/v1/movies/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/pricing-plans", Set.of("get", "post"));
        operations.put("/admin/api/v1/pricing-plans/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/discount-codes", Set.of("get", "post"));
        operations.put("/admin/api/v1/discount-codes/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/refund-policies", Set.of("get", "post"));
        operations.put("/admin/api/v1/refund-policies/{id}", Set.of("get", "put", "delete"));
        operations.put("/admin/api/v1/screenings", Set.of("post", "get"));
        operations.put("/admin/api/v1/screenings/{id}", Set.of("get", "delete"));
        operations.put("/admin/api/v1/screenings/{id}/prices", Set.of("get"));
        return new TreeMap<>(operations);
    }

    private void assertProblem(ApiResponse response, int status, String code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(response.requestId()).isNotNull();
        assertThatCodeIsUuid(response.requestId());
        assertThat(response.body().get("type").asText()).startsWith("/problems/");
        assertThat(response.body().get("title").asText()).isNotBlank();
        assertThat(response.body().get("status").asInt()).isEqualTo(status);
        assertThat(response.body().get("detail").asText()).isNotBlank();
        assertThat(response.body().get("instance").asText()).startsWith("/");
        assertThat(response.body().get("code").asText()).isEqualTo(code);
        assertThat(response.body().get("requestId").asText()).isEqualTo(response.requestId());
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
