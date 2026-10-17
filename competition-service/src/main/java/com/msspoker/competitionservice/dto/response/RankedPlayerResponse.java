package com.msspoker.competitionservice.dto.response;

import java.util.UUID;

/** One row of ranking-service GET /internal/ranking/top, best player first. */
public record RankedPlayerResponse(long position, UUID accountId, int elo, String tier, int winStreak,
                                   int matchesPlayed) {
}
