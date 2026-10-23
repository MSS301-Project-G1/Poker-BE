package com.msspoker.gameservice.dto.response;

import java.util.UUID;

public record PlacementResponse(UUID accountId, int place, long finalChips) {
}
