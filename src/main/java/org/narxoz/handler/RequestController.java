package org.narxoz.handler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.narxoz.config.RequestService;
import org.narxoz.domain.PurchaseRequest;
import org.narxoz.domain.RequestId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP adapter. The controller chooses the status code; the service still does the work.
 * It never touches EntityManager and never returns the entity.
 */
@RestController
@RequestMapping("/api/v1/purchase-requests")
@Tag(name = "Purchase requests")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create one purchase request (starts as DRAFT)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; Location points to the new request",
                    content = @Content(schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION: a field is blank or too long, or the body is not JSON",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "DUPLICATE: this business key already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequestBody body) {
        PurchaseRequest created = service.register(RequestId.newId(), body.businessKey(), body.title());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id().uuid())
                .toUri();
        return ResponseEntity.created(location).body(RequestResponse.from(created));
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read one purchase request by its UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found",
                    content = @Content(schema = @Schema(implementation = RequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION: the id is not a UUID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "NOT_FOUND: no purchase request with this id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RequestResponse get(@PathVariable UUID id) {
        return RequestResponse.from(service.get(new RequestId(id.toString())));
    }
}
