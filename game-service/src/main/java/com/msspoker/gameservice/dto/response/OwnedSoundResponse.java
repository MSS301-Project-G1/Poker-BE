package com.msspoker.gameservice.dto.response;

import java.util.UUID;

/** shop-service envelope of GET /internal/inventory/{accountId}/sounds/{productId}. */
public record OwnedSoundResponse(Sound data) {
    public record Sound(UUID productId, String soundUrl) {
    }
}
