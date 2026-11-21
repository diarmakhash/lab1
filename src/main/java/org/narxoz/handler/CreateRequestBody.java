package org.narxoz.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body of POST /api/v1/purchase-requests.
 * Validation annotations live here, in the web package, never in domain.
 * Messages are fixed in English so the error text does not depend on the laptop's locale.
 */
public record CreateRequestBody(
        @Schema(example = "PR-19", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        String businessKey,

        @Schema(example = "Laptops for the lab", maxLength = 120, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 120, message = "size must be between 0 and 120")
        String title) {
}
