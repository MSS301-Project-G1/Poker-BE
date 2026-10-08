package com.msspoker.gameservice.dto.response;

import java.util.UUID;

public record TableCreatedResponse(UUID tableId, UUID matchId) {
}
