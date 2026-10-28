package com.sparkz.sparkz.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.util.UUID;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@Profile("!local & !test")
public class MatchSecurity {
    @Bean
    SecurityFilterChain matchSecurity(HttpSecurity http,
            @Value("${sparkz.auth.issuer:}") String issuer,
            @Value("${sparkz.auth.audience:authenticated}") String audience) throws Exception {
        http.securityMatcher("/api/match-requests", "/api/match-requests/**", "/api/discovery", "/api/discovery/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable()); // Bearer tokens only; no cookie authentication.
        if (issuer.isBlank()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        } else {
            NimbusJwtDecoder decoder = decoder(issuer, audience);
            http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)));
        }
        return http.build();
    }
    // Supabase publishes JWKS directly; an OIDC discovery document is not required.
    static NimbusJwtDecoder decoder(String issuer, String audience) {
        String jwks = issuer.replaceAll("/+$", "") + "/.well-known/jwks.json";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks)
            .jwsAlgorithms(algorithms -> {
                algorithms.add(SignatureAlgorithm.RS256);
                algorithms.add(SignatureAlgorithm.ES256);
            }).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean validSubject;
            try {
                String subject = jwt.getSubject();
                validSubject = subject != null && UUID.fromString(subject).toString().equalsIgnoreCase(subject);
            } catch (IllegalArgumentException e) { validSubject = false; }
            return validSubject && jwt.getExpiresAt() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid required claims", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), claims));
        return decoder;
    }
}
