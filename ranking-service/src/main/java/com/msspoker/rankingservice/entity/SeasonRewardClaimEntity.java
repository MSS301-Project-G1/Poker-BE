package com.msspoker.rankingservice.entity;

import com.msspoker.rankingservice.enums.Tier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One claim per player and season covers every reward up to the claimed tier. */
@Entity
@Table(name = "season_reward_claims")
@Getter
@Setter
public class SeasonRewardClaimEntity {
    @Id private UUID id;
    private UUID seasonId;
    private UUID accountId;
    @Enumerated(EnumType.STRING) private Tier tier;
    private Instant claimedAt;
}
