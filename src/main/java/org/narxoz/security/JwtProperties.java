package org.narxoz.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.* from yaml. The secret is never a Java literal: dev has a classroom value,
 * prod reads CSS_JWT_SECRET. A short secret fails at startup, not at the first login.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, String issuer) {
    static final int MIN_SECRET_LENGTH = 32; // 256 bits for HS256

    public JwtProperties {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("app.jwt.secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.jwt.issuer must not be blank");
        }
    }
}
