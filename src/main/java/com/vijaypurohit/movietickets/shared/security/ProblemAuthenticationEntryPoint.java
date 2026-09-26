package com.vijaypurohit.movietickets.shared.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter responseWriter;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"movie-tickets\"");
        responseWriter.write(
                HttpStatus.UNAUTHORIZED,
                "authentication-required",
                "Authentication required",
                "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource.",
                request,
                response);
    }
}
