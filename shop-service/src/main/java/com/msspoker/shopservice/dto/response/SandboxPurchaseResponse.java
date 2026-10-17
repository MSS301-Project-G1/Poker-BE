package com.msspoker.shopservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SandboxPurchaseResponse(
        UUID accountId,
        String bundleCode,
        String bundleName,
        long vipCoinCredited,
        String message
) {}
