package org.narxoz.handler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.narxoz.security.TokenService;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only place where a password is sent. A wrong name or a wrong password throws
 * an AuthenticationException; the advice turns both into the same 401.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {
    private final AuthenticationManager auth;
    private final TokenService tokens;

    public AuthController(AuthenticationManager auth, TokenService tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Log in with a dev user and get a 15-minute JWT")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "VALIDATION: username or password is blank",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: bad username or password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        Authentication user = auth.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        return new TokenResponse(tokens.issue(user), "Bearer", TokenService.LIFETIME.toSeconds());
    }
}
