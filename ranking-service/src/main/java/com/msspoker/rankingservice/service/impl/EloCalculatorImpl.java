package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.service.EloCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EloCalculatorImpl implements EloCalculator {
    private final RankingProperties properties;

    @Override
    public int delta(int place) {
        List<Integer> table = properties.getEloByPlace();
        if (place < 1 || table.isEmpty()) throw RankingExceptions.invalidPlace();
        return table.get(Math.min(place, table.size()) - 1);
    }

    @Override
    public int apply(int elo, int delta) {
        return Math.max(properties.getMinElo(), elo + delta);
    }

    @Override
    public Tier tier(int elo) {
        List<Integer> thresholds = properties.getTierThresholds();
        Tier[] tiers = Tier.values();
        Tier result = Tier.NEWBIE;
        // CARD_GOD has no threshold; it is derived from the leaderboard position.
        for (int i = 0; i < thresholds.size() && i < tiers.length - 1; i++) {
            if (elo >= thresholds.get(i)) result = tiers[i];
        }
        return result;
    }

    @Override
    public Tier displayTier(Tier tier, long position) {
        return tier == Tier.GRAND_CHAMPION && position <= properties.getCardGodLimit() ? Tier.CARD_GOD : tier;
    }

    @Override
    public boolean extendsWinStreak(int place) {
        return place <= properties.getWinStreakPlaces();
    }

    @Override
    public long reward(int place, long entryFee) {
        List<Long> multipliers = properties.getRewardMultipliers();
        return place >= 1 && place <= multipliers.size() ? entryFee * multipliers.get(place - 1) : 0;
    }

    @Override
    public int carryOver(int elo, int keepPercent) {
        return Math.max(properties.getMinElo(), elo * keepPercent / 100);
    }
}
