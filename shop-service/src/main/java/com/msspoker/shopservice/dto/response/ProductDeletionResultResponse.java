package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ProductDeletionResultResponse(
        UUID productId,
        String productName,
        int totalUsersRefunded,
        long totalVipCoinRefunded,
        String message
) {
}

