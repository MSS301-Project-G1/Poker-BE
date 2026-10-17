package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.Tier;

import java.util.UUID;

/** GET /internal/ranking/{accountId}: SPEC fields elo, tier, winStreak, lossStreak. */
public record PlayerRankingResponse(UUID accountId, int elo, Tier tier, int winStreak, int lossStreak) {
}
