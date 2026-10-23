package com.msspoker.rankingservice.validator;

import com.msspoker.rankingservice.dto.request.TierRewardRequest;
import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.exception.RankingExceptions;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TierRewardsValidator {
    /** One coin amount per tier, and a product appears at most once per tier. */
    public void validate(List<TierRewardRequest> rewards) {
        if (rewards == null) return;
        Set<String> seen = new HashSet<>();
        for (TierRewardRequest reward : rewards) {
            String key = reward.tier() + ":" + (reward.rewardType() == RewardType.COIN ? "COIN" : reward.productId());
            if (!seen.add(key)) throw RankingExceptions.duplicateTierReward();
        }
    }
}
