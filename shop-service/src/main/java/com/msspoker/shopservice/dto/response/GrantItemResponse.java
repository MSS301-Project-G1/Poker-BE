package com.msspoker.shopservice.dto.response;

import lombok.Builder;

@Builder
public record GrantItemResponse(
        boolean granted,
        boolean alreadyOwned,
        InventoryItemResponse item
) {}
