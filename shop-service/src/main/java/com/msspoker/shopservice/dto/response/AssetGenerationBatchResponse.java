package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AssetGenerationBatchResponse(
        UUID batchId,
        String categoryCode,
        UUID productId,
        String modelName,
        String s3ZipUrl,
        String s3PreviewUrl,
        String styleSpec,
        String status,
        int fileCount,
        Instant createdAt,
        Instant expiresAt
) {}
