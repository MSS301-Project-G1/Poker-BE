package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Validated
@ConfigurationProperties("auth.wallet")
public record WalletProperties(@NotNull URI baseUrl, @NotNull Duration connectTimeout, @NotNull Duration requestTimeout) {
    public WalletProperties {
        if (baseUrl != null && (!Set.of("http", "https").contains(baseUrl.getScheme())
                || baseUrl.getHost() == null || baseUrl.getRawQuery() != null || baseUrl.getFragment() != null
                || baseUrl.getUserInfo() != null)) {
            throw new IllegalArgumentException("Wallet URL must be an HTTP(S) base URL");
        }
        if ((connectTimeout != null && (connectTimeout.isZero() || connectTimeout.isNegative()))
                || (requestTimeout != null && (requestTimeout.isZero() || requestTimeout.isNegative()))) {
            throw new IllegalArgumentException("Wallet timeouts must be positive");
        }
    }
}
