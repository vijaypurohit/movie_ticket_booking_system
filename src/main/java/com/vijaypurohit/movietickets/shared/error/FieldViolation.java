package com.vijaypurohit.movietickets.shared.error;

/**
 * A validation failure for one request field. The published shape lives in the
 * OpenAPI specification as the {@code FieldViolation} schema.
 */
public record FieldViolation(String field, String code, String message) {
}
