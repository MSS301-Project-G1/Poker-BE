package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CoinBundleResponse(
        UUID id,
        String code,
        String name,
        long priceVnd,
        long baseVipCoin,
        long bonusVipCoin,
        long totalVipCoin,
        int bonusPercentage,
        String badgeLabel,
        String iconUrl,
        boolean isActive,
        int sortOrder
) {}
