package com.vijaypurohit.movietickets.shared.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.vijaypurohit.movietickets.shared.request.RequestIdContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI INVALID_REQUEST_TYPE = URI.create("/problems/invalid-request");

    private final ProblemDetailsFactory problemDetailsFactory;

    public GlobalExceptionHandler(ProblemDetailsFactory problemDetailsFactory) {
        this.problemDetailsFactory = problemDetailsFactory;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException exception, HttpServletRequest request) {
        ProblemDetail problem = problemDetailsFactory.create(
                exception.status(),
                exception.type(),
                exception.title(),
                exception.code(),
                exception.getMessage(),
                request,
                List.of());
        return response(exception.status(), problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                        violation.getMessage()))
                .toList();
        ProblemDetail problem = invalidRequest("Request validation failed.", request, violations);
        return response(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailsFactory.create(
                HttpStatus.UNAUTHORIZED,
                URI.create("/problems/authentication-required"),
                "Authentication required",
                "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource.",
                request,
                List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"movie-tickets\"");
        return response(headers, HttpStatus.UNAUTHORIZED, problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailsFactory.create(
                HttpStatus.CONFLICT,
                URI.create("/problems/data-conflict"),
                "Data conflict",
                "DATA_CONFLICT",
                "The request conflicts with an existing or referenced record.",
                request,
                List.of());
        return response(HttpStatus.CONFLICT, problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problemDetailsFactory.create(
                HttpStatus.FORBIDDEN,
                URI.create("/problems/access-denied"),
                "Access denied",
                "ACCESS_DENIED",
                "You are not authorized to access this resource.",
                request,
                List.of());
        return response(HttpStatus.FORBIDDEN, problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected request failure requestId={} exceptionType={}",
                RequestIdContext.from(request),
                exception.getClass().getName());
        ProblemDetail problem = problemDetailsFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                URI.create("/problems/internal-error"),
                "Internal server error",
                "INTERNAL_ERROR",
                "The request could not be completed.",
                request,
                List.of());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        List<FieldViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    String code = error.getCode() == null ? "INVALID" : error.getCode();
                    String message = error.getDefaultMessage() == null
                            ? "The value is invalid."
                            : error.getDefaultMessage();
                    return new FieldViolation(field, code, message);
                })
                .toList();
        HttpServletRequest request = servletRequest(webRequest);
        return response(HttpStatus.BAD_REQUEST, invalidRequest("Request validation failed.", request, violations));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        HttpServletRequest request = servletRequest(webRequest);
        return response(HttpStatus.BAD_REQUEST, invalidRequest("The request body is malformed.", request, List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        List<FieldViolation> violations = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName == null
                    ? "argument[" + result.getMethodParameter().getParameterIndex() + ']'
                    : parameterName;
            result.getResolvableErrors().forEach(error -> violations.add(new FieldViolation(
                    field,
                    validationCode(error),
                    error.getDefaultMessage() == null ? "The value is invalid." : error.getDefaultMessage())));
        });
        exception.getCrossParameterValidationResults().forEach(error -> violations.add(new FieldViolation(
                "request",
                validationCode(error),
                error.getDefaultMessage() == null ? "The request is invalid." : error.getDefaultMessage())));
        HttpServletRequest request = servletRequest(webRequest);
        return response(HttpStatus.BAD_REQUEST, invalidRequest("Request validation failed.", request, violations));
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        return notFound(servletRequest(webRequest));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest webRequest) {
        return notFound(servletRequest(webRequest));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest webRequest) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpServletRequest request = servletRequest(webRequest);
        if (status == HttpStatus.BAD_REQUEST) {
            return response(headers, status, invalidRequest("The request is invalid.", request, List.of()));
        }
        if (status == HttpStatus.NOT_FOUND) {
            return notFound(request);
        }

        String slug;
        String code;
        String title;
        String detail;
        if (status == HttpStatus.METHOD_NOT_ALLOWED) {
            slug = "method-not-allowed";
            code = "METHOD_NOT_ALLOWED";
            title = "Method not allowed";
            detail = "The HTTP method is not supported for this resource.";
        } else if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) {
            slug = "unsupported-media-type";
            code = "UNSUPPORTED_MEDIA_TYPE";
            title = "Unsupported media type";
            detail = "The request content type is not supported.";
        } else if (status == HttpStatus.NOT_ACCEPTABLE) {
            slug = "not-acceptable";
            code = "NOT_ACCEPTABLE";
            title = "Not acceptable";
            detail = "The requested response content type is not supported.";
        } else {
            slug = status.is5xxServerError() ? "internal-error" : "request-failed";
            code = status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
            title = status.is5xxServerError() ? "Internal server error" : "Request failed";
            detail = status.is5xxServerError()
                    ? "The request could not be completed."
                    : "The request could not be processed.";
        }

        if (status.is5xxServerError()) {
            LOGGER.error(
                    "Unexpected framework failure requestId={} exceptionType={}",
                    RequestIdContext.from(request),
                    exception.getClass().getName());
        }
        ProblemDetail problem = problemDetailsFactory.create(
                status,
                URI.create("/problems/" + slug),
                title,
                code,
                detail,
                request,
                List.of());
        return response(headers, status, problem);
    }

    private ResponseEntity<Object> notFound(HttpServletRequest request) {
        ProblemDetail problem = problemDetailsFactory.create(
                HttpStatus.NOT_FOUND,
                URI.create("/problems/resource-not-found"),
                "Resource not found",
                "RESOURCE_NOT_FOUND",
                "The requested resource was not found.",
                request,
                List.of());
        return response(HttpStatus.NOT_FOUND, problem);
    }

    private ProblemDetail invalidRequest(
            String detail,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        return problemDetailsFactory.create(
                HttpStatus.BAD_REQUEST,
                INVALID_REQUEST_TYPE,
                "Invalid request",
                "INVALID_REQUEST",
                detail,
                request,
                violations);
    }

    private HttpServletRequest servletRequest(WebRequest webRequest) {
        return ((ServletWebRequest) webRequest).getRequest();
    }

    private String validationCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "INVALID" : codes[0];
    }

    private ResponseEntity<Object> response(HttpStatus status, ProblemDetail problem) {
        return response(new HttpHeaders(), status, problem);
    }

    private ResponseEntity<Object> response(HttpHeaders headers, HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
