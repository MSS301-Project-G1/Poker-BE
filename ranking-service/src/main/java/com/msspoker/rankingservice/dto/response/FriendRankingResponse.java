package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.Tier;

import java.util.UUID;

/**
 * One row of the friends leaderboard. rank orders the player and their friends; position is the
 * season leaderboard position, null for friends without a ranked match this season.
 */
public record FriendRankingResponse(int rank, Long position, UUID accountId, int elo, Tier tier, int winStreak,
                                    int matchesPlayed, boolean you) {
}
