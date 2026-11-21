package org.narxoz.handler;

import io.swagger.v3.oas.annotations.media.Schema;

/** Every failure body: code, message, correlationId. No stack trace, no exception class. */
public record ErrorResponse(
        @Schema(example = "VALIDATION") String code,
        @Schema(example = "title must not be blank") String message,
        @Schema(example = "c0ffee00-1234-4abc-9def-000000000001") String correlationId) {
}
