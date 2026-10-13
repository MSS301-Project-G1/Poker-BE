package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.Tier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rewards of one ended season: every reward up to the player's final tier (SPEC §2.3 cumulative). */
public record SeasonRewardResponse(UUID seasonId, String seasonName, Instant endAt, Tier finalTier,
                                   List<TierRewardResponse> rewards, boolean claimed, Instant claimedAt) {
}
