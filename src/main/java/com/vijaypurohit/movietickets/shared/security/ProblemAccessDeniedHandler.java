package com.vijaypurohit.movietickets.shared.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter responseWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        responseWriter.write(
                HttpStatus.FORBIDDEN,
                "access-denied",
                "Access denied",
                "ACCESS_DENIED",
                "You are not authorized to access this resource.",
                request,
                response);
    }
}
