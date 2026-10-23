package com.msspoker.rankingservice.entity;

import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.Tier;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_tier_rewards")
@Getter
@Setter
public class SeasonTierRewardEntity {
    @Id private UUID id;
    private UUID seasonId;
    @Enumerated(EnumType.STRING) private Tier tier;
    @Enumerated(EnumType.STRING) private RewardType rewardType;
    private long amount;
    private UUID productId;
}
