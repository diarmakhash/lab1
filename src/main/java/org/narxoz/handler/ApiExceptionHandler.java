package org.narxoz.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.narxoz.domain.DuplicateRequest;
import org.narxoz.domain.RequestNotFound;
import org.narxoz.domain.TransitionForbidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The one place that knows HTTP status codes for failures.
 * Every failure body is ErrorResponse: code, message, correlationId.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> words(error.getField()) + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> badPathId(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", ex.getName() + " must be a UUID", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> notJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", "body must be JSON", request);
    }

    @ExceptionHandler(RequestNotFound.class)
    ResponseEntity<ErrorResponse> notFound(RequestNotFound ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "purchase request not found", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noSuchPath(NoResourceFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "no such path", request);
    }

    @ExceptionHandler(DuplicateRequest.class)
    ResponseEntity<ErrorResponse> duplicate(DuplicateRequest ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE", "business key already exists", request);
    }

    @ExceptionHandler(TransitionForbidden.class)
    ResponseEntity<ErrorResponse> transitionForbidden(TransitionForbidden ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "TRANSITION_FORBIDDEN", ex.getMessage(), request);
    }

    /** Login: unknown user and wrong password are the same answer, so names cannot be probed. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> badLogin(AuthenticationException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "bad username or password", request);
    }

    /** @PreAuthorize fails inside the controller call, so it lands here, not in the filter. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", SecurityErrorHandlers.NOT_ALLOWED, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED", "content type must be application/json", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "UNSUPPORTED", "method not allowed", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("unexpected error, correlationId={}", correlationId, ex); // the text stays in the log
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(CORRELATION_HEADER, correlationId)
                .body(new ErrorResponse("INTERNAL", "unexpected error", correlationId));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                                HttpServletRequest request) {
        String correlationId = correlationId(request);
        return ResponseEntity.status(status)
                .header(CORRELATION_HEADER, correlationId)
                .body(new ErrorResponse(code, message, correlationId));
    }

    /** Repeat the caller's X-Correlation-Id; mint a new one if it is missing or blank. */
    static String correlationId(HttpServletRequest request) {
        String given = request.getHeader(CORRELATION_HEADER);
        return (given == null || given.isBlank()) ? UUID.randomUUID().toString() : given.trim();
    }

    /** businessKey -> "business key", so the message reads like the assignment's table. */
    static String words(String field) {
        return field.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
    }
}
