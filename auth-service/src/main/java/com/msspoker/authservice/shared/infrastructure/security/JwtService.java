package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.shared.infrastructure.config.AuthJwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final RSAKey signingKey;
    private final AuthJwtProperties properties;
    private final Clock clock;

    public String issue(Account account, Instant expiresAt) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(account.getId().toString())
                .issuedAt(now).notBefore(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("role", account.getRole().name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(signingKey.getKeyID()).build(), claims)).getTokenValue();
    }

    public Jwt verify(String token) {
        Jwt jwt = decoder.decode(token);
        try {
            UUID.fromString(jwt.getSubject());
            AccountRole.valueOf(jwt.getClaimAsString("role"));
            if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant())
                    || jwt.getNotBefore() == null || jwt.getId() == null) {
                throw new IllegalArgumentException("Invalid access claims");
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new JwtException("Invalid access claims");
        }
        return jwt;
    }

    public Map<String, Object> publicKeys() {
        // Only the public key is published; never serialize the signing key directly.
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
