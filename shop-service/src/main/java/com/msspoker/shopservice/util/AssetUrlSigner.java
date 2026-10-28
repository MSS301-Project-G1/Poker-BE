package com.msspoker.shopservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.msspoker.shopservice.config.SharedAssetProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces links into the private team bucket with presigned GET URLs when responses are mapped, as
 * auth-service does for avatars. Once the bucket policy makes the folder public (shop.shared-assets.public-read)
 * the links go out plain instead, unchanged between responses so browsers can cache the files; a link stored
 * presigned loses its stale signature either way. Other links pass through unchanged, and so does everything
 * while the folder is private and no credentials are configured (the browser then gets 403 from the bucket).
 */
@Slf4j
@Component
@EnableConfigurationProperties(SharedAssetProperties.class)
public class AssetUrlSigner {

    public static final String SIGN_URL = "signAssetUrl";
    public static final String SIGN_METADATA = "signAssetMetadata";

    private final SharedAssetProperties properties;
    private final ObjectMapper objectMapper;
    /** Null when the bucket is not configured. */
    private final MinioClient presigner;

    public AssetUrlSigner(SharedAssetProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.publicRead()) {
            presigner = null;
            log.info("shop.shared-assets.public-read is on; links into the team bucket are returned plain");
        } else if (properties.isConfigured()) {
            // With the region given, presigning is a local computation and never calls S3.
            presigner = MinioClient.builder()
                    .endpoint("https://s3." + properties.region() + ".amazonaws.com")
                    .region(properties.region())
                    .credentials(properties.accessKey(), properties.secretKey())
                    .build();
        } else {
            presigner = null;
            log.warn("shop.shared-assets is not configured; links into the team bucket are returned unsigned");
        }
    }

    @Named(SIGN_URL)
    public String signUrl(String url) {
        String key = signableKey(url);
        if (key == null) {
            return url;
        }
        if (properties.publicRead()) {
            return properties.bucketUrl() + key;
        }
        try {
            return presigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(key)
                    .expiry(Math.toIntExact(properties.urlTtl().toSeconds()))
                    .build());
        } catch (Exception e) {
            // The client declares network exceptions, but local signing only fails on bad configuration.
            log.warn("Could not sign asset link {}: {}", key, e.getMessage());
            return url;
        }
    }

    /** Signs every bucket link inside the product metadata JSON, e.g. the 53 card images of a deck. */
    @Named(SIGN_METADATA)
    public String signMetadata(String metadataJson) {
        if (!rewritesLinks() || metadataJson == null || !metadataJson.contains(properties.bucketUrl())) {
            return metadataJson;
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            signTextNodes(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Product metadata is not valid JSON; returned unsigned: {}", e.getMessage());
            return metadataJson;
        }
    }

    private void signTextNodes(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode child = object.get(name);
                if (child.isTextual()) {
                    object.put(name, signUrl(child.asText()));
                } else {
                    signTextNodes(child);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode child = array.get(i);
                if (child.isTextual()) {
                    array.set(i, TextNode.valueOf(signUrl(child.asText())));
                } else {
                    signTextNodes(child);
                }
            }
        }
    }

    private boolean rewritesLinks() {
        return presigner != null || properties.publicRead();
    }

    /** The object key when the link points under the signed prefix of the team bucket, else null. */
    private String signableKey(String url) {
        if (!rewritesLinks() || url == null || !url.startsWith(properties.bucketUrl())) {
            return null;
        }
        String key = url.substring(properties.bucketUrl().length());
        int query = key.indexOf('?');
        if (query >= 0) {
            // A link stored already presigned (it expires) is signed again from its key.
            key = key.substring(0, query);
        }
        return key.startsWith(properties.signedPrefix()) && !key.contains("..") ? key : null;
    }
}
