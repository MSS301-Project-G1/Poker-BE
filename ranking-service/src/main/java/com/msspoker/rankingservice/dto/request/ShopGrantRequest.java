package com.msspoker.rankingservice.dto.request;

import java.util.UUID;

/** Body of shop-service POST /internal/inventory/grant (SPEC §6.1). */
public record ShopGrantRequest(UUID accountId, UUID productId, String idempotencyKey) {
}
