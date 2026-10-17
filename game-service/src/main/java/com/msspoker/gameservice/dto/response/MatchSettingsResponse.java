package com.msspoker.gameservice.dto.response;

import com.msspoker.gameservice.enums.GameMode;

import java.util.UUID;

public record MatchSettingsResponse(UUID id, GameMode mode, long smallBlind, long bigBlind, long startingChips,
                            int turnTimeSeconds, int minPlayers, int maxPlayers) {
}
