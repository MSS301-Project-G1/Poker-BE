package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth.cookie")
public record AuthCookieProperties(boolean secure, @NotBlank String sameSite) {
    public AuthCookieProperties {
        if (sameSite != null && !java.util.Set.of("Lax", "Strict", "None").contains(sameSite)) {
            throw new IllegalArgumentException("Cookie SameSite must be Lax, Strict or None");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        }
    }
}
