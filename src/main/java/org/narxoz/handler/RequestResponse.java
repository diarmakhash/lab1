package org.narxoz.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import org.narxoz.domain.PurchaseRequest;

import java.util.UUID;

/**
 * JSON body of a purchase request on the wire: id, business key, status token, title.
 * Built from the domain record, never from the JPA entity.
 */
public record RequestResponse(
        @Schema(example = "3f2a6c1e-8b1e-4c9a-a04d-5c1f0b7e2d91") UUID id,
        @Schema(example = "PR-19") String businessKey,
        @Schema(example = "DRAFT") String status,
        @Schema(example = "Laptops for the lab") String title) {

    static RequestResponse from(PurchaseRequest request) {
        return new RequestResponse(
                request.id().uuid(),
                request.businessKey().value(),
                request.status().name(),
                request.title());
    }
}
