package com.vijaypurohit.movietickets.shared.request;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.vijaypurohit.movietickets.shared.identifier.IdGenerator;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private final IdGenerator idGenerator;

    public RequestIdFilter(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = idGenerator.nextId().toString();
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, requestId);
        response.setHeader(RequestIdContext.HEADER_NAME, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(RequestIdContext.MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
        }
    }
}
