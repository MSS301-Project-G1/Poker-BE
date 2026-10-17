package com.msspoker.rankingservice.dto.response;

import java.time.Instant;

/** rewardCoins is the coins granted once completed, otherwise the coins the player will receive. */
public record TutorialResponse(boolean completed, Instant completedAt, long rewardCoins) {
}
