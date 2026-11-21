package org.narxoz.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.servlet.DispatcherType;
import org.narxoz.handler.SecurityErrorHandlers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Who is calling, and may they. The rules below are the README rules table, top to bottom:
 * the first match wins, so the narrow "approve" rule sits above the wide /api/v1/** rule.
 */
@Configuration
@EnableMethodSecurity // turns on @PreAuthorize: the second lock on RequestService.approve
@EnableConfigurationProperties(JwtProperties.class)
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {
    public static final String REQUESTER = "REQUESTER"; // junior: creates and reads purchase requests
    public static final String APPROVER = "APPROVER";   // senior: may also approve

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityErrorHandlers errors) throws Exception {
        return http
                .authorizeHttpRequests(rules -> rules
                        // Boot's own error dispatch: keeps the original status instead of turning it into 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // the auth page: exact files only, so nothing here opens /api
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/app.css", "/app.js", "/favicon.ico").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/purchase-requests/*/approve").hasRole(APPROVER)
                        .requestMatchers(HttpMethod.GET, "/api/me").hasAnyRole(REQUESTER, APPROVER)
                        .requestMatchers("/api/v1/**").hasAnyRole(REQUESTER, APPROVER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken()))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errors)   // no token at all -> our 401 body
                        .accessDeniedHandler(errors))       // wrong role -> our 403 body
                .csrf(csrf -> csrf.disable())               // the credential is a header, not a cookie
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .build();
    }

    /** Roles live in the "roles" claim; hasRole("APPROVER") looks for ROLE_APPROVER. */
    static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    SecretKey jwtKey(JwtProperties jwt) {
        return new SecretKeySpec(jwt.secret().getBytes(UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /** Checks the HS256 signature, the expiry and that iss is our product. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey, JwtProperties jwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.issuer()));
        return decoder;
    }

    /** BCrypt by default; stored values look like {bcrypt}$2a$10$... */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Two classroom users, laptop only. Prod ships no users. */
    @Bean
    @Profile("dev")
    UserDetailsService devUsers(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("aigerim").password(encoder.encode("requester-dev")).roles(REQUESTER).build(),
                User.withUsername("daniyar").password(encoder.encode("approver-dev")).roles(APPROVER).build());
    }

    /** "Find the user, compare the hash." Without a user list (prod) every login is 401. */
    @Bean
    AuthenticationManager authenticationManager(ObjectProvider<UserDetailsService> users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(users.getIfAvailable(() -> username -> {
            throw new UsernameNotFoundException(username);
        }));
        return new ProviderManager(provider);
    }
}
