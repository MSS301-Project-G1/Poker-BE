package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.ItemSource;
import com.msspoker.shopservice.enums.ProductTier;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InventoryItemResponse(
        UUID id,
        UUID productId,
        String productName,
        String categoryCode,
        String categoryName,
        String assetUrl,
        String previewUrl,
        String previewVideoUrl,
        String metadata,
        ProductTier tier,
        ItemSource source,
        boolean isEquipped,
        Instant acquiredAt
) {}

