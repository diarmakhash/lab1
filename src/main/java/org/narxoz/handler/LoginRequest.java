package org.narxoz.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** JSON body of POST /api/v1/auth/login. The only request that carries a password. */
public record LoginRequest(
        @Schema(example = "daniyar", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        String username,

        @Schema(example = "approver-dev", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]"; // never in a log line
    }
}
