package com.vijaypurohit.movietickets.shared.error;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.vijaypurohit.movietickets.shared.request.RequestIdContext;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class ProblemDetailsFactory {

    public ProblemDetail create(
            HttpStatus status,
            URI type,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            List<FieldViolation> fieldErrors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("requestId", RequestIdContext.from(request));
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", List.copyOf(fieldErrors));
        }
        return problem;
    }
}
