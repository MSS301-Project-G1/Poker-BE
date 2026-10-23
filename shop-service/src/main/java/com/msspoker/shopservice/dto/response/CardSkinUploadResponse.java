package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CardSkinUploadResponse(
        UUID productId,
        String productName,
        int totalCardsUploaded,
        String version,
        String previewUrl,
        String textureAtlasUrl,
        String coordinatesUrl,
        CardDeckManifest manifest
) {}
