package com.msspoker.shopservice.dto.request;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ApplyBatchRequest(
        UUID targetProductId,
        UUID categoryId,
        String productName,
        String tier,
        String description,
        Long priceVipCoin,
        Long priceVnd
) {}
