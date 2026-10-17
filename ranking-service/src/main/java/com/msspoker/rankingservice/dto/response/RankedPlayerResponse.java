package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.Tier;

import java.util.UUID;

public record RankedPlayerResponse(long position, UUID accountId, int elo, Tier tier, int winStreak, int matchesPlayed) {
}
