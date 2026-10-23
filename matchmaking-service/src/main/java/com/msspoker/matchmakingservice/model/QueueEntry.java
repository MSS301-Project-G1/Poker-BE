package com.msspoker.matchmakingservice.model;

import com.msspoker.matchmakingservice.enums.MatchMode;

import java.time.Instant;
import java.util.UUID;

public record QueueEntry(
        UUID accountId,
        MatchMode mode,
        Instant joinedAt,
        UUID partyId,
        Integer elo,
        int winStreak,
        int lossStreak
) {
    public QueueEntry(UUID accountId, MatchMode mode, Instant joinedAt) {
        this(accountId, mode, joinedAt, null, null, 0, 0);
    }

    public QueueEntry(UUID accountId, MatchMode mode, Instant joinedAt, UUID partyId) {
        this(accountId, mode, joinedAt, partyId, null, 0, 0);
    }

    public static QueueEntry rank(UUID accountId, Instant joinedAt, int elo, int winStreak, int lossStreak) {
        return new QueueEntry(accountId, MatchMode.RANK, joinedAt, null, elo, winStreak, lossStreak);
    }

    public boolean belongsToParty() {
        return partyId != null;
    }

    public int rankForm() {
        return winStreak - lossStreak;
    }
}
