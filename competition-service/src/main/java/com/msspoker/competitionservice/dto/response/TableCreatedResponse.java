package com.msspoker.competitionservice.dto.response;

import java.util.UUID;

/** Response of game-service CreateTable. */
public record TableCreatedResponse(UUID tableId, UUID matchId) {
}
