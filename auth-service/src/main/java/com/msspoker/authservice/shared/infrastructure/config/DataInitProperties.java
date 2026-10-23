package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Demo accounts from DATA_INIT_* variables; bound only when auth.data-init.enabled=true. */
@Validated
@ConfigurationProperties("auth.data-init")
public record DataInitProperties(
        @Valid @NotNull SeedAccount admin,
        @Valid @NotNull SeedAccount user,
        @Valid @NotNull SeedAccount secondaryUser,
        @Valid @NotNull SeedAccount unverifiedUser) {

    public record SeedAccount(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName) {
    }
}
