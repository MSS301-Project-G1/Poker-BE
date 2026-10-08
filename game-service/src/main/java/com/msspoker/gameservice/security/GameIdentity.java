package com.msspoker.gameservice.security;

import com.msspoker.gameservice.exception.GameExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GameIdentity {
    private final Environment environment;
    @Value("${game.jwt.jwk-set-uri:}") private String jwkSetUri;
    @Value("${game.jwt.issuer:}") private String issuer;
    @Value("${game.jwt.audience:}") private String audience;
    private volatile JwtDecoder decoder;

    public Principal authenticate(String authorization, String devUser) {
        try {
            UUID id;
            if (environment.acceptsProfiles(Profiles.of(GameHeaders.DEV_PROFILE)) && devUser != null) {
                id = UUID.fromString(devUser);
            } else {
                if (authorization == null || !authorization.startsWith("Bearer ")) throw GameExceptions.unauthorized();
                Jwt jwt = decoder().decode(authorization.substring("Bearer ".length()));
                if (!jwt.getAudience().contains(audience)) throw GameExceptions.unauthorized();
                id = UUID.fromString(jwt.getSubject());
            }
            return id::toString;
        } catch (IllegalArgumentException | JwtException ex) {
            throw GameExceptions.unauthorized();
        }
    }

    private JwtDecoder decoder() {
        if (jwkSetUri.isBlank() || issuer.isBlank() || audience.isBlank()) throw GameExceptions.unauthorized();
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder configured = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                    configured.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
                    decoder = configured;
                }
            }
        }
        return decoder;
    }
}
