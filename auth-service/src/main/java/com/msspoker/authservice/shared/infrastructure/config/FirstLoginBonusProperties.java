package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("auth.first-login-bonus")
public record FirstLoginBonusProperties(@Min(1) long rewardCoins, @NotNull Duration retryBackoff) {
    public FirstLoginBonusProperties {
        if (retryBackoff != null && (retryBackoff.isZero() || retryBackoff.isNegative())) {
            throw new IllegalArgumentException("First-login bonus retry backoff must be positive");
        }
    }
}
