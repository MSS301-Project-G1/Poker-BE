package com.msspoker.shopservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Seed products and test grants from shop.data-init.*; bound only when shop.data-init.enabled=true.
 *
 * @param assetBaseUrl   public URL of the shared bucket folder that holds the seed files
 * @param authServiceUrl auth-service base URL, which resolves demo emails to account ids
 * @param grantEmails    auth-service demo accounts that receive every seed product, for testing
 */
@Validated
@ConfigurationProperties("shop.data-init")
public record DataInitProperties(
        @NotBlank String assetBaseUrl,
        @NotBlank String authServiceUrl,
        List<String> grantEmails) {

    public DataInitProperties {
        grantEmails = grantEmails == null ? List.of()
                : grantEmails.stream().filter(email -> email != null && !email.isBlank()).toList();
    }
}
