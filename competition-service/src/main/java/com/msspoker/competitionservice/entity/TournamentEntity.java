package com.msspoker.competitionservice.entity;

import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournaments")
@Getter
@Setter
public class TournamentEntity {
    @Id private UUID id;
    private String name;
    @Enumerated(EnumType.STRING) private TournamentType type;
    private Instant startAt;
    private long entryFee;
    private int slots;
    @Enumerated(EnumType.STRING) private TournamentStatus status;
    /** Coins added to the prize pool; the whole ELITE pool, a bonus on top of OPEN entry fees. */
    private long rewardCoins;
    /** Exclusive skin granted to the champion. */
    private UUID rewardProductId;
    private UUID createdBy;
    private Instant createdAt;
    /** ELITE: when the top of the leaderboard was invited. */
    private Instant invitesSentAt;
    /** ELITE: when unconfirmed invitations expired and the next ranks were invited. */
    private Instant replacementsSentAt;
    private boolean isDeleted;
}
