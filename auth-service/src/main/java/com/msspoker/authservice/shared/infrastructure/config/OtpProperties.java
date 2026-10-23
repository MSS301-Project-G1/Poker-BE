package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("auth.otp")
public record OtpProperties(@NotNull Duration ttl, @Min(1) int maxAttempts, @NotNull Duration resendCooldown) {
    public OtpProperties {
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("OTP TTL must be positive");
        }
        if (resendCooldown != null && (resendCooldown.isZero() || resendCooldown.isNegative())) {
            throw new IllegalArgumentException("OTP resend cooldown must be positive");
        }
    }
}
