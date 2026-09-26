package com.vijaypurohit.movietickets.shared.error;

import java.net.URI;
import java.util.Objects;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final URI type;
    private final String title;
    private final String code;

    protected ApiException(
            HttpStatus status,
            String problemType,
            String title,
            String code,
            String detail) {
        super(Objects.requireNonNull(detail, "detail is required"));
        this.status = Objects.requireNonNull(status, "status is required");
        this.type = URI.create("/problems/" + Objects.requireNonNull(problemType, "problemType is required"));
        this.title = Objects.requireNonNull(title, "title is required");
        this.code = Objects.requireNonNull(code, "code is required");
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    public String code() {
        return code;
    }
}
