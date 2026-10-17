package com.msspoker.matchmakingservice.dto.response;

import java.util.UUID;

public record TableCreatedResponse(UUID tableId, UUID matchId) {
}
