package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.Tier;

import java.time.Instant;
import java.util.UUID;

/** Current player's season standing; position is null until the player has an Elo record. */
public record MyRankingResponse(UUID seasonId, Long position, UUID accountId, int elo, int peakElo, Tier tier,
                                int winStreak, int lossStreak, int matchesPlayed, Instant lastMatchAt) {
}
