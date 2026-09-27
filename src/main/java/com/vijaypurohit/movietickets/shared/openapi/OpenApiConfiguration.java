package com.vijaypurohit.movietickets.shared.openapi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";
    public static final String IDEMPOTENCY_KEY_PARAMETER = "IdempotencyKey";
    public static final String PAGE_PARAMETER = "Page";
    public static final String PAGE_SIZE_PARAMETER = "PageSize";
    public static final String CURSOR_PARAMETER = "Cursor";
    public static final String CURSOR_LIMIT_PARAMETER = "CursorLimit";

    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetails";
    private static final String COMPONENT_PARAMETER_PREFIX = "#/components/parameters/";
    private static final String COMPONENT_RESPONSE_PREFIX = "#/components/responses/";

    @Bean
    OpenAPI movieTicketsOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BASIC_AUTH_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"))
                .addParameters(IDEMPOTENCY_KEY_PARAMETER, new HeaderParameter()
                        .name("Idempotency-Key")
                        .description("Unique key used to safely retry a state-changing request")
                        .required(true)
                        .example("demo-reservation-001")
                        .schema(new StringSchema().minLength(1).maxLength(120)))
                .addParameters(PAGE_PARAMETER, new Parameter()
                        .in("query")
                        .name("page")
                        .description("Zero-based page index")
                        .schema(new IntegerSchema()._default(0).minimum(java.math.BigDecimal.ZERO)))
                .addParameters(PAGE_SIZE_PARAMETER, new Parameter()
                        .in("query")
                        .name("size")
                        .description("Number of records from 1 to 100")
                        .schema(new IntegerSchema()
                                ._default(20)
                                .minimum(java.math.BigDecimal.ONE)
                                .maximum(java.math.BigDecimal.valueOf(100))))
                .addParameters(CURSOR_PARAMETER, new Parameter()
                        .in("query")
                        .name("cursor")
                        .description("Opaque cursor returned by the previous response")
                        .example("eyJ2IjoxLCJrIjo...")
                        .schema(new StringSchema()))
                .addParameters(CURSOR_LIMIT_PARAMETER, new Parameter()
                        .in("query")
                        .name("limit")
                        .description("Number of records from 1 to 100")
                        .schema(new IntegerSchema()
                                ._default(20)
                                .minimum(java.math.BigDecimal.ONE)
                                .maximum(java.math.BigDecimal.valueOf(100))))
                .addResponses("BadRequest", problemResponse(400, "Invalid request", "INVALID_REQUEST",
                        "The request is malformed or fails validation."))
                .addResponses("Unauthenticated", problemResponse(401, "Authentication required", "AUTHENTICATION_REQUIRED",
                        "Valid HTTP Basic credentials are required."))
                .addResponses("Forbidden", problemResponse(403, "Access denied", "ACCESS_DENIED",
                        "The authenticated account lacks the required role."))
                .addResponses("NotFound", problemResponse(404, "Resource not found", "RESOURCE_NOT_FOUND",
                        "The resource does not exist or is concealed from this account."))
                .addResponses("Conflict", problemResponse(409, "Request conflict", "REQUEST_CONFLICT",
                        "The request conflicts with the current resource state."))
                .addResponses("BusinessViolation", problemResponse(422, "Business rule violation", "BUSINESS_RULE_VIOLATION",
                        "A business rule prevents the operation."))
                .addResponses("InternalError", problemResponse(500, "Internal server error", "INTERNAL_ERROR",
                        "An unexpected failure occurred."));

        return new OpenAPI()
                .info(new Info()
                        .title("Movie Ticket Booking API")
                        .version("v1")
                        .description("Browse screenings, reserve seats, complete bookings, cancel bookings, and administer the local catalog."))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development")))
                .tags(List.of(
                        new Tag().name("Public catalog").description("Unauthenticated city, theater, and movie discovery"),
                        new Tag().name("Public screenings").description("Unauthenticated screening and seat-availability discovery"),
                        new Tag().name("Customer reservations").description("Customer seat holds and release"),
                        new Tag().name("Customer bookings").description("Customer checkout, history, and cancellation"),
                        new Tag().name("Customer refunds").description("Customer refund status"),
                        new Tag().name("Admin catalog").description("Administrator catalog management"),
                        new Tag().name("Admin pricing").description("Administrator pricing, discount, and refund-policy management"),
                        new Tag().name("Admin screenings").description("Administrator screening management")))
                .components(components);
    }

    @Bean
    OpenApiCustomizer completeContract() {
        return openApi -> {
            openApi.getComponents()
                    .addSchemas("FieldViolation", fieldViolationSchema())
                    .addSchemas("ProblemDetails", problemDetailsSchema());
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
                replaceReusableParameters(operation.getParameters());
                addStandardResponses(path, method.name(), operation.getResponses());
                addRequestExample(path, method.name(), operation.getRequestBody());
            }));
        };
    }

    private static ApiResponse problemResponse(int status, String title, String code, String description) {
        return new ApiResponse()
                .description(description)
                .addHeaderObject("X-Request-Id", new io.swagger.v3.oas.models.headers.Header()
                        .description("Server-generated request correlation identifier")
                        .schema(new StringSchema().format("uuid")))
                .content(new io.swagger.v3.oas.models.media.Content()
                        .addMediaType(PROBLEM_MEDIA_TYPE, new MediaType()
                                .schema(new Schema<>().$ref(PROBLEM_SCHEMA))
                                .example(Map.of(
                                        "type", "/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-'),
                                        "title", title,
                                        "status", status,
                                        "detail", "The request could not be processed.",
                                        "instance", "/api/v1/seat-reservations",
                                        "code", code,
                                        "requestId", "8f3d8cbe-0e42-4d9b-9d50-96199b173ba7"))));
    }

    private static void replaceReusableParameters(List<Parameter> parameters) {
        if (parameters == null) return;
        parameters.replaceAll(parameter -> {
            String component = switch (parameter.getName()) {
                case "page" -> PAGE_PARAMETER;
                case "size" -> PAGE_SIZE_PARAMETER;
                case "cursor" -> CURSOR_PARAMETER;
                case "limit" -> CURSOR_LIMIT_PARAMETER;
                default -> null;
            };
            return component == null ? parameter : new Parameter().$ref(COMPONENT_PARAMETER_PREFIX + component);
        });
    }

    private static void addStandardResponses(String path, String method, ApiResponses responses) {
        if (responses == null) return;
        responses.putIfAbsent("400", responseReference("BadRequest"));
        responses.putIfAbsent("500", responseReference("InternalError"));
        if (isProtected(path)) {
            responses.putIfAbsent("401", responseReference("Unauthenticated"));
            responses.putIfAbsent("403", responseReference("Forbidden"));
        }
        if (path.contains("{")) responses.putIfAbsent("404", responseReference("NotFound"));
        if (!"GET".equals(method)) {
            responses.putIfAbsent("409", responseReference("Conflict"));
            responses.putIfAbsent("422", responseReference("BusinessViolation"));
        }
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/admin/api/v1/")
                || path.startsWith("/api/v1/seat-reservations")
                || path.startsWith("/api/v1/bookings")
                || path.startsWith("/api/v1/refunds");
    }

    private static ApiResponse responseReference(String component) {
        return new ApiResponse().$ref(COMPONENT_RESPONSE_PREFIX + component);
    }

    private static void addRequestExample(String path, String method,
            io.swagger.v3.oas.models.parameters.RequestBody requestBody) {
        if (requestBody == null || requestBody.getContent() == null) return;
        Map<String, Object> example = requestExample(path, method);
        if (example == null) return;
        requestBody.getContent().values().forEach(mediaType -> mediaType.setExample(example));
    }

    private static Map<String, Object> requestExample(String path, String method) {
        if (!("POST".equals(method) || "PUT".equals(method))) return null;
        if (path.matches("/admin/api/v1/cities(?:/\\{id})?"))
            return Map.of("name", "Pune", "country", "India", "timeZone", "Asia/Kolkata");
        if (path.matches("/admin/api/v1/theaters(?:/\\{id})?"))
            return Map.of("cityId", sampleId(1), "name", "Central Cinema", "address", "Baner, Pune");
        if (path.contains("/auditoriums") && !path.contains("/seats"))
            return Map.of("name", "Screen 1");
        if (path.contains("/seats") && !path.contains("screenings"))
            return Map.of("rowLabel", "A", "seatNumber", 1, "category", "REGULAR");
        if (path.matches("/admin/api/v1/movies(?:/\\{id})?"))
            return Map.of("title", "The Last Commit", "durationMinutes", 120, "language", "English");
        if (path.matches("/admin/api/v1/pricing-plans(?:/\\{id})?"))
            return Map.of("name", "Standard INR", "regularPrice", new BigDecimal("250.00"),
                    "premiumPrice", new BigDecimal("400.00"), "weekendAdjustment", new BigDecimal("50.00"));
        if (path.matches("/admin/api/v1/discount-codes(?:/\\{id})?"))
            return Map.of("code", "WELCOME10", "type", "PERCENTAGE", "value", new BigDecimal("10.00"),
                    "validFrom", "2030-01-01T00:00:00Z", "validUntil", "2030-12-31T23:59:59Z",
                    "minimumSpend", new BigDecimal("200.00"), "maximumDiscount", new BigDecimal("100.00"),
                    "globalUsageLimit", 1000, "perCustomerUsageLimit", 1);
        if (path.matches("/admin/api/v1/refund-policies(?:/\\{id})?"))
            return Map.of("name", "Standard refund policy", "rules", List.of(
                    Map.of("cutoffMinutes", 1440, "refundPercentage", new BigDecimal("100.00")),
                    Map.of("cutoffMinutes", 120, "refundPercentage", new BigDecimal("50.00")),
                    Map.of("cutoffMinutes", 0, "refundPercentage", new BigDecimal("0.00"))));
        if ("/admin/api/v1/screenings".equals(path))
            return Map.of("movieId", sampleId(2), "auditoriumId", sampleId(3),
                    "pricingPlanId", sampleId(4), "refundPolicyId", sampleId(5),
                    "startTime", "2030-01-07T12:30:00Z", "endTime", "2030-01-07T14:30:00Z");
        if ("/api/v1/seat-reservations".equals(path))
            return Map.of("screeningSeatIds", List.of(sampleId(6), sampleId(7)));
        if ("/api/v1/bookings".equals(path))
            return Map.of("reservationId", sampleId(8), "paymentToken", "tok_success");
        return null;
    }

    private static String sampleId(int suffix) {
        return "00000000-0000-4000-8000-%012d".formatted(suffix);
    }

    private ObjectSchema fieldViolationSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("field", new StringSchema().example("screeningSeatIds[1]"));
        schema.addProperty("code", new StringSchema().example("NotNull"));
        schema.addProperty("message", new StringSchema().example("The field is required."));
        schema.setRequired(List.of("field", "code", "message"));
        return schema;
    }

    private ObjectSchema problemDetailsSchema() {
        Schema<Object> fieldViolationReference = new Schema<>();
        fieldViolationReference.set$ref("#/components/schemas/FieldViolation");

        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("type", new StringSchema().format("uri").example("/problems/seat-unavailable"));
        schema.addProperty("title", new StringSchema().example("Seat unavailable"));
        schema.addProperty("status", new IntegerSchema().format("int32").example(409));
        schema.addProperty("detail", new StringSchema()
                .example("One or more requested seats are no longer available."));
        schema.addProperty("instance", new StringSchema().format("uri").example("/api/v1/seat-reservations"));
        schema.addProperty("code", new StringSchema().example("SEAT_UNAVAILABLE"));
        schema.addProperty("requestId", new StringSchema().format("uuid")
                .example("8f3d8cbe-0e42-4d9b-9d50-96199b173ba7"));
        schema.addProperty("fieldErrors", new ArraySchema().items(fieldViolationReference));
        schema.setRequired(List.of("type", "title", "status", "detail", "instance", "code", "requestId"));
        return schema;
    }
}
