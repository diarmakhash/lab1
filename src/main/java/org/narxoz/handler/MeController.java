package org.narxoz.handler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Who is logged in: read from the Authentication the filter built from the token, never from a body. */
@RestController
@Tag(name = "Auth")
@SecurityRequirement(name = "bearer")
public class MeController {

    public record MeResponse(
            @Schema(example = "daniyar") String username,
            @Schema(example = "[\"ROLE_APPROVER\"]") List<String> authorities) {
    }

    @GetMapping(path = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The username and authorities from the caller's token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller",
                    content = @Content(schema = @Schema(implementation = MeResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: valid token required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public MeResponse me(Authentication authentication) {
        return new MeResponse(
                authentication.getName(),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
}
