package com.msspoker.competitionservice.dto.request;

import com.msspoker.competitionservice.enums.GameMode;

import java.util.List;
import java.util.UUID;

/** Body of game-service POST /internal/tables (SPEC §6.1). playerIds order is the seat order. */
public record CreateTableRequest(GameMode mode, UUID sourceId, List<UUID> playerIds,
                                 TableSettingsRequest settings, long entryFee) {
}
