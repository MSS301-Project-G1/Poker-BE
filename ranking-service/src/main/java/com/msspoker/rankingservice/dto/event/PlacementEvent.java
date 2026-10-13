package com.msspoker.rankingservice.dto.event;

import java.util.UUID;

public record PlacementEvent(UUID accountId, int place, long finalChips) {
}
