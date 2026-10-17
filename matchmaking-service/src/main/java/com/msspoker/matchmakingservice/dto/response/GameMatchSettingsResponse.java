package com.msspoker.matchmakingservice.dto.response;

import com.msspoker.matchmakingservice.enums.MatchMode;

public record GameMatchSettingsResponse(MatchMode mode, int minPlayers, int maxPlayers) {
    public GameMatchSettingsResponse {
        if (mode == null || minPlayers < 2 || maxPlayers < minPlayers) {
            throw new IllegalArgumentException("Game Match Settings không hợp lệ");
        }
    }
}
