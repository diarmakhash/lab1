package org.narxoz.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Writes the pass: iss, sub, iat, exp and roles, signed HS256 with our secret.
 * No password, no IIN, no phone: anyone can read the payload.
 */
@Service
public class TokenService {
    public static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder encoder;
    private final String issuer;

    public TokenService(JwtEncoder encoder, JwtProperties jwt) {
        this.encoder = encoder;
        this.issuer = jwt.issuer();
    }

    public String issue(Authentication user) {
        Instant now = Instant.now();
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)                       // "ROLE_APPROVER"
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length())) // "APPROVER"
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getName())
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
