package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.ProductTier;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductResponse(
        UUID id,
        String categoryCode,
        String categoryName,
        String name,
        String description,
        ProductTier tier,
        long priceVnd,
        long priceVipCoin,
        String assetUrl,
        String previewUrl,
        String previewVideoUrl,
        String metadata,
        boolean isExclusive,
        boolean isOwned
) {
    public ProductResponse withOwned(boolean owned) {
        return new ProductResponse(
                id, categoryCode, categoryName, name, description, tier,
                priceVnd, priceVipCoin, assetUrl, previewUrl, previewVideoUrl, metadata, isExclusive, owned
        );
    }
}
