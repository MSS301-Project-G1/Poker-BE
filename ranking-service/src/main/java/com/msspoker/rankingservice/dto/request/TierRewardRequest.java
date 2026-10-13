package com.msspoker.rankingservice.dto.request;

import com.msspoker.rankingservice.enums.RewardType;
import com.msspoker.rankingservice.enums.Tier;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** COIN needs a positive amount; PRODUCT needs a shop product id. */
public record TierRewardRequest(@NotNull Tier tier, @NotNull RewardType rewardType, Long amount, UUID productId) {
    @AssertTrue(message = "Quà xu cần số xu dương; quà vật phẩm cần productId.")
    public boolean isRewardValid() {
        if (rewardType == RewardType.COIN) return amount != null && amount > 0 && productId == null;
        return rewardType != RewardType.PRODUCT || productId != null;
    }
}
