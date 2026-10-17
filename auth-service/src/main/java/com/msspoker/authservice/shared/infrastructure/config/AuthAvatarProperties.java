package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("auth.avatar")
public record AuthAvatarProperties(@NotBlank @Size(max = 1800) String publicBaseUrl) {
}
