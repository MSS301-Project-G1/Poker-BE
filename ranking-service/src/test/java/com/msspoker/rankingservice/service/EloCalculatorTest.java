package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.service.impl.EloCalculatorImpl;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EloCalculatorTest {
    private final EloCalculator calculator = new EloCalculatorImpl(new RankingProperties());

    @Test
    void placesFollowSpecTableAndReuseLastValueBeyondSix() {
        assertArrayEquals(new int[]{20, 10, 5, -5, -10, -15, -15, -15},
                IntStream.rangeClosed(1, 8).map(calculator::delta).toArray());
        assertThrows(IllegalArgumentException.class, () -> calculator.delta(0));
    }

    @Test
    void eloNeverDropsBelowZero() {
        assertEquals(0, calculator.apply(3, -10));
        assertEquals(0, calculator.apply(0, -15));
        assertEquals(15, calculator.apply(30, -15));
        assertEquals(1620, calculator.apply(1600, 20));
    }

    @Test
    void tierThresholdsMatchSpecBoundaries() {
        Map<Integer, Tier> expected = Map.ofEntries(
                Map.entry(0, Tier.NEWBIE), Map.entry(99, Tier.NEWBIE),
                Map.entry(100, Tier.IRON), Map.entry(199, Tier.IRON),
                Map.entry(200, Tier.BRONZE), Map.entry(349, Tier.BRONZE),
                Map.entry(350, Tier.SILVER), Map.entry(499, Tier.SILVER),
                Map.entry(500, Tier.GOLD), Map.entry(699, Tier.GOLD),
                Map.entry(700, Tier.PLATINUM), Map.entry(949, Tier.PLATINUM),
                Map.entry(950, Tier.DIAMOND), Map.entry(1249, Tier.DIAMOND),
                Map.entry(1250, Tier.CHAMPION), Map.entry(1599, Tier.CHAMPION),
                Map.entry(1600, Tier.GRAND_CHAMPION), Map.entry(50_000, Tier.GRAND_CHAMPION));
        expected.forEach((elo, tier) -> assertEquals(tier, calculator.tier(elo), "Elo " + elo));
    }

    @Test
    void cardGodIsTopFiftyGrandChampionsOnly() {
        assertEquals(Tier.CARD_GOD, calculator.displayTier(Tier.GRAND_CHAMPION, 1));
        assertEquals(Tier.CARD_GOD, calculator.displayTier(Tier.GRAND_CHAMPION, 50));
        assertEquals(Tier.GRAND_CHAMPION, calculator.displayTier(Tier.GRAND_CHAMPION, 51));
        assertEquals(Tier.CHAMPION, calculator.displayTier(Tier.CHAMPION, 1));
    }

    @Test
    void topThreeExtendWinStreakAndEarnFeeMultiples() {
        assertTrue(calculator.extendsWinStreak(3));
        assertFalse(calculator.extendsWinStreak(4));
        assertEquals(300, calculator.reward(1, 100));
        assertEquals(200, calculator.reward(2, 100));
        assertEquals(100, calculator.reward(3, 100));
        assertEquals(0, calculator.reward(4, 100));
        assertEquals(0, calculator.reward(1, 0));
    }
}
