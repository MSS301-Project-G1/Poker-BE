package com.msspoker.competitionservice.dto.event;

import java.util.UUID;

public record PlacementEvent(UUID accountId, int place, long finalChips) {
}
