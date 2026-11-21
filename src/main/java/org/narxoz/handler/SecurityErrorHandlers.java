package org.narxoz.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 and 403 happen in the filters, before any controller, so the advice never sees them.
 * These two handlers write the same ErrorResponse as every other failure.
 */
@Component
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {
    static final String TOKEN_REQUIRED = "valid token required";
    static final String NOT_ALLOWED = "not allowed for your role";

    private final ObjectMapper json;

    public SecurityErrorHandlers(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", TOKEN_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", NOT_ALLOWED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       int status, String code, String message) throws IOException {
        String correlationId = ApiExceptionHandler.correlationId(request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(ApiExceptionHandler.CORRELATION_HEADER, correlationId);
        json.writeValue(response.getOutputStream(), new ErrorResponse(code, message, correlationId));
    }
}
