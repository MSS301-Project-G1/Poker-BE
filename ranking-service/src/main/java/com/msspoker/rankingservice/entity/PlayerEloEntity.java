package com.msspoker.rankingservice.entity;

import com.msspoker.rankingservice.enums.Tier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_elo")
@Getter
@Setter
public class PlayerEloEntity {
    @Id private UUID id;
    private UUID seasonId;
    private UUID accountId;
    private int currentElo;
    private int peakElo;
    /** Tier by Elo threshold; CARD_GOD is derived when reading the leaderboard. */
    @Enumerated(EnumType.STRING) private Tier tier;
    private int winStreak;
    private int lossStreak;
    private int matchesPlayed;
    private Instant lastMatchAt;
    /** Displayed tier (including CARD_GOD) captured when the season closed; null while it is active. */
    @Enumerated(EnumType.STRING) private Tier finalTier;
    private boolean isDeleted;
}
