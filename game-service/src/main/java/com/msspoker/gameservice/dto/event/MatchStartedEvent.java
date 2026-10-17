package com.msspoker.gameservice.dto.event;

import com.msspoker.gameservice.enums.GameMode;

import java.util.List;
import java.util.UUID;

public record MatchStartedEvent(UUID matchId, UUID tableId, GameMode mode, UUID sourceId, List<UUID> playerIds) {
}
