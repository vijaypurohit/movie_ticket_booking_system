package com.vijaypurohit.movietickets.shared.openapi;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";
    public static final String IDEMPOTENCY_KEY_PARAMETER = "IdempotencyKey";
    public static final String PAGE_PARAMETER = "Page";
    public static final String PAGE_SIZE_PARAMETER = "PageSize";
    public static final String CURSOR_PARAMETER = "Cursor";
    public static final String CURSOR_LIMIT_PARAMETER = "CursorLimit";

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
                        .schema(new StringSchema()))
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
                        .schema(new StringSchema()))
                .addParameters(CURSOR_LIMIT_PARAMETER, new Parameter()
                        .in("query")
                        .name("limit")
                        .description("Number of records from 1 to 100")
                        .schema(new IntegerSchema()
                                ._default(20)
                                .minimum(java.math.BigDecimal.ONE)
                                .maximum(java.math.BigDecimal.valueOf(100))));

        return new OpenAPI()
                .info(new Info()
                        .title("Movie Ticket Booking API")
                        .version("v1")
                        .description("APIs for movie discovery, seat reservation, booking, payment, and cancellation"))
                .components(components);
    }

    @Bean
    OpenApiCustomizer commonProblemSchemas() {
        return openApi -> openApi.getComponents()
                .addSchemas("FieldViolation", fieldViolationSchema())
                .addSchemas("ProblemDetails", problemDetailsSchema());
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
