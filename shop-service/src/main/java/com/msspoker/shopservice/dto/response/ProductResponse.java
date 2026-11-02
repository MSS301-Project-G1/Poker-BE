package com.msspoker.shopservice.dto.response;

import com.msspoker.shopservice.enums.ProductStatus;
import com.msspoker.shopservice.enums.ProductTier;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ProductResponse(
        UUID id,
        UUID categoryId,
        String categoryCode,
        String categoryName,
        String name,
        String description,
        ProductTier tier,
        ProductStatus status,
        long priceVnd,
        long priceVipCoin,
        String assetUrl,
        String previewUrl,
        String previewVideoUrl,
        String metadata,
        boolean isExclusive,
        boolean isOwned,
        boolean isDeleting,
        Instant deletionScheduledAt,
        String deletionNotice,
        Instant saleExpiresAt
) {
    public ProductResponse withOwned(boolean owned) {
        return new ProductResponse(
                id, categoryId, categoryCode, categoryName, name, description, tier, status,
                priceVnd, priceVipCoin, assetUrl, previewUrl, previewVideoUrl, metadata,
                isExclusive, owned, isDeleting, deletionScheduledAt, deletionNotice,
                saleExpiresAt
        );
    }

    public boolean isFlashSaleActive() {
        return !isExclusive && saleExpiresAt != null && saleExpiresAt.isAfter(Instant.now());
    }
}
