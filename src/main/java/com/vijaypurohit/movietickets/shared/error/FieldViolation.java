package com.vijaypurohit.movietickets.shared.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A validation failure for one request field")
public record FieldViolation(
        @Schema(example = "screeningSeatIds[1]") String field,
        @Schema(example = "NotNull") String code,
        @Schema(example = "The field is required.") String message) {
}
