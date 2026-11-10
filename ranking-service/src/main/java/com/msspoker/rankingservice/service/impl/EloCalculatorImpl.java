package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.service.EloCalculator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EloCalculatorImpl implements EloCalculator {
    private final RankingProperties properties;

    /**
     * A table at least as large as the list uses it by place; places beyond it reuse the last value. A smaller
     * table splits in half: the top half takes the first values, the bottom half the last ones (so the last place
     * always loses most) and the middle seat of an odd table keeps its Elo. 2 players: +20, -15; 3: +20, 0, -15.
     */
    @Override
    public int delta(int place, int players) {
        List<Integer> table = properties.getEloByPlace();
        if (place < 1 || place > players || table.isEmpty()) throw RankingExceptions.invalidPlace();
        if (players >= table.size()) return table.get(Math.min(place, table.size()) - 1);
        int half = players / 2;
        int fromLast = players - place;
        if (place <= half) return table.get(place - 1);
        return fromLast < half ? table.get(table.size() - 1 - fromLast) : 0;
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
    public boolean extendsWinStreak(int place, int players) {
        return place <= properties.getWinStreakPlaces() && delta(place, players) > 0;
    }

    /** 6 players: 3x, 2x, 1x the fee; 2 players: 2x; 4 players: 3x, 1x. Never more than the fees paid in. */
    @Override
    public long reward(int place, int players, long entryFee) {
        List<Long> multipliers = properties.getRewardMultipliers();
        long feesLeft = players;
        for (int i = 0; i < Math.min(place, multipliers.size()); i++) {
            long share = Math.min(multipliers.get(i), feesLeft);
            if (i == place - 1) return entryFee * share;
            feesLeft -= share;
        }
        return 0;
    }

    @Override
    public int carryOver(int elo, int keepPercent) {
        return Math.max(properties.getMinElo(), elo * keepPercent / 100);
    }
}
