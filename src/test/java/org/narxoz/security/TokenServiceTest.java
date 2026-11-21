package org.narxoz.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** No web layer, no mocks: TokenService signs, a JwtDecoder with the same secret reads it back. */
class TokenServiceTest {
    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";
    private static final SecretKey KEY = new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256");

    private final TokenService tokens = new TokenService(
            new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), new JwtProperties(SECRET, "purchases"));

    private static JwtDecoder decoderFor(String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private String issueFor(String username, String authority) {
        return tokens.issue(UsernamePasswordAuthenticationToken.authenticated(
                username, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Test
    void issuedTokenCarriesUserRoleAndExpiry() {
        Jwt jwt = decoderFor("purchases").decode(issueFor("aigerim", "ROLE_REQUESTER"));

        assertEquals("aigerim", jwt.getSubject());
        assertEquals(List.of("REQUESTER"), jwt.getClaimAsStringList("roles")); // no ROLE_ prefix in the token
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String token = issueFor("aigerim", "ROLE_REQUESTER"); // iss = purchases

        assertThrows(JwtException.class, () -> decoderFor("helpdesk").decode(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        SecretKey otherKey = new SecretKeySpec("another-secret-also-32-bytes-long!!".getBytes(UTF_8), "HmacSHA256");
        TokenService otherSigner = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), new JwtProperties(SECRET, "purchases"));
        String forged = otherSigner.issue(UsernamePasswordAuthenticationToken.authenticated(
                "aigerim", null, List.of(new SimpleGrantedAuthority("ROLE_APPROVER"))));

        assertThrows(JwtException.class, () -> decoderFor("purchases").decode(forged));
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties("secret", "purchases"));
    }
}
