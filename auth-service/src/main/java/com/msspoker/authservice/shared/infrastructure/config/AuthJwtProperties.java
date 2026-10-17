package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Validated
@ConfigurationProperties("auth.jwt")
public record AuthJwtProperties(@NotBlank String issuer, @NotBlank String audience,
        @NotNull Duration accessTtl, @NotNull Duration refreshTtl,
        @NotNull Path privateKeyPath, boolean generateKey) {
    public AuthJwtProperties {
        if (accessTtl != null && (accessTtl.toSeconds() < 1)) {
            throw new IllegalArgumentException("Access token TTL must be at least one second");
        }
        if (refreshTtl != null && accessTtl != null && refreshTtl.compareTo(accessTtl) < 0) {
            throw new IllegalArgumentException("Refresh token TTL must be at least the access token TTL");
        }
    }
}
