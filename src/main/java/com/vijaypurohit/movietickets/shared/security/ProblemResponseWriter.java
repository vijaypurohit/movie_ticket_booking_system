package com.vijaypurohit.movietickets.shared.security;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.shared.error.ProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemResponseWriter {

    private final ProblemDetailsFactory problemDetailsFactory;
    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ProblemDetailsFactory problemDetailsFactory, ObjectMapper objectMapper) {
        this.problemDetailsFactory = problemDetailsFactory;
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpStatus status,
            String problemType,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        ProblemDetail problem = problemDetailsFactory.create(
                status,
                URI.create("/problems/" + problemType),
                title,
                code,
                detail,
                request,
                List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
