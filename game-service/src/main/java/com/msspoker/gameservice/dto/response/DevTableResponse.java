package com.msspoker.gameservice.dto.response;

import java.util.List;
import java.util.UUID;

public record DevTableResponse(UUID tableId, UUID matchId, List<UUID> humanIds, List<UUID> botIds) {
}
