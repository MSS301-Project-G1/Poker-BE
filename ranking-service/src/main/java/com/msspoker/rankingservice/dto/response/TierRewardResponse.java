package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.Tier;

import java.util.UUID;

public record TierRewardResponse(Tier tier, RewardType rewardType, long amount, UUID productId) {
}
