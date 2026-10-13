package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.RoomStatus;

import java.util.UUID;

public record RoomSummaryResponse(UUID id, String roomCode, UUID ownerId, boolean hasPassword, int playerCount,
                                  int maxPlayers, long smallBlind, long bigBlind, RoomStatus status) {
}
