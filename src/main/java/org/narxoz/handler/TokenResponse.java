package org.narxoz.handler;

import io.swagger.v3.oas.annotations.media.Schema;

/** What a good login returns. expiresIn is seconds: 900 = 15 minutes. */
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn) {
}
