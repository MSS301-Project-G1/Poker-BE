package com.msspoker.authservice.shared.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("auth.s3")
public record S3Properties(boolean enabled, String bucket, @NotBlank String region, String accessKey, String secretKey,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9/_-]+") String avatarPrefix,
        @NotNull Duration uploadTtl, @NotNull Duration viewTtl, @NotNull DataSize maxFileSize, URI endpoint) {
    @AssertTrue(message = "Enabled S3 requires bucket, access key and secret key")
    public boolean isConfigured() {
        return !enabled || (bucket != null && !bucket.isBlank() && accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank());
    }

    @AssertTrue(message = "Invalid S3 URL expiry or avatar size limit")
    public boolean isLimitsValid() {
        return uploadTtl != null && uploadTtl.compareTo(Duration.ofSeconds(1)) >= 0
                && uploadTtl.compareTo(Duration.ofHours(1)) <= 0 && viewTtl != null
                && viewTtl.compareTo(Duration.ofSeconds(1)) >= 0 && viewTtl.compareTo(Duration.ofDays(7)) <= 0
                && maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
