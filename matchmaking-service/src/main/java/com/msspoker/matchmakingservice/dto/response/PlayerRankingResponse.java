package com.msspoker.matchmakingservice.dto.response;

import java.util.UUID;

public record PlayerRankingResponse(UUID accountId, int elo, int winStreak, int lossStreak) {
    public PlayerRankingResponse {
        if (accountId == null || elo < 0 || winStreak < 0 || lossStreak < 0) {
            throw new IllegalArgumentException("Ranking response không hợp lệ");
        }
    }
}
