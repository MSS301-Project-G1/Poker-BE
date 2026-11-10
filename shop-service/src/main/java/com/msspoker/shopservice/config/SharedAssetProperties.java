package com.msspoker.shopservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * The private team bucket (same bucket and IAM user as auth-service avatars) from shop.shared-assets.*.
 *
 * @param signedPrefix only keys under it are signed, so product data cannot expose other private files
 * @param urlTtl       lifetime of signed links; must outlive the catalog cache, which stores mapped responses
 * @param publicRead   the bucket policy lets anyone read signedPrefix: links go out plain, so browsers can cache them
 */
@ConfigurationProperties("shop.shared-assets")
public record SharedAssetProperties(
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        String signedPrefix,
        Duration urlTtl,
        boolean publicRead) {

    public boolean isConfigured() {
        return notBlank(bucket) && notBlank(region) && notBlank(accessKey) && notBlank(secretKey)
                && notBlank(signedPrefix) && urlTtl != null;
    }

    /** Virtual-hosted URL that links into the bucket start with. */
    public String bucketUrl() {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
