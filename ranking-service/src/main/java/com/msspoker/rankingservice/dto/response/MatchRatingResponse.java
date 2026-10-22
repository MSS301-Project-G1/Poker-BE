package com.msspoker.rankingservice.dto.response;

import java.util.UUID;

/** The caller's applied Elo change and coin reward for one processed RANK match; rewardCoins is 0 below top 3. */
public record MatchRatingResponse(UUID matchId, int place, int eloDelta, int eloAfter, long rewardCoins) {
}
