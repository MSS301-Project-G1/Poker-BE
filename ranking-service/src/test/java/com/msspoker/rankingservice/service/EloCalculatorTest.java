package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.config.RankingProperties;
import com.msspoker.rankingservice.enums.Tier;
import com.msspoker.rankingservice.service.impl.EloCalculatorImpl;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class EloCalculatorTest {
    private final EloCalculator calculator = new EloCalculatorImpl(new RankingProperties());

    @Test
    void fullTableFollowsSpecAndLargerTablesReuseTheLastValue() {
        assertArrayEquals(new int[]{20, 10, 5, -5, -10, -15}, deltas(6));
        assertArrayEquals(new int[]{20, 10, 5, -5, -10, -15, -15, -15}, deltas(8));
        assertThrows(IllegalArgumentException.class, () -> calculator.delta(0, 6));
        assertThrows(IllegalArgumentException.class, () -> calculator.delta(3, 2));
    }

    @Test
    void smallTablesSplitIntoGainersAndLosers() {
        assertArrayEquals(new int[]{20, -15}, deltas(2));
        assertArrayEquals(new int[]{20, 0, -15}, deltas(3));
        assertArrayEquals(new int[]{20, 10, -10, -15}, deltas(4));
        assertArrayEquals(new int[]{20, 10, 0, -10, -15}, deltas(5));
        // Every table size adds the same +5 to the pool as the full SPEC table.
        IntStream.rangeClosed(2, 6).forEach(players -> assertEquals(5, IntStream.of(deltas(players)).sum(), players + " players"));
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
    void placesThatGainEloExtendTheWinStreak() {
        assertTrue(calculator.extendsWinStreak(3, 6));
        assertFalse(calculator.extendsWinStreak(4, 6));
        assertTrue(calculator.extendsWinStreak(1, 2));
        assertFalse(calculator.extendsWinStreak(2, 2));
        assertFalse(calculator.extendsWinStreak(2, 3));
        assertTrue(calculator.extendsWinStreak(2, 5));
        assertFalse(calculator.extendsWinStreak(3, 5));
    }

    @Test
    void rewardsPayFeeMultiplesWithinTheFeesOfTheTable() {
        assertArrayEquals(new long[]{300, 200, 100, 0, 0, 0}, rewards(6));
        assertArrayEquals(new long[]{200, 0}, rewards(2));
        assertArrayEquals(new long[]{300, 0, 0}, rewards(3));
        assertArrayEquals(new long[]{300, 100, 0, 0}, rewards(4));
        assertArrayEquals(new long[]{300, 200, 0, 0, 0}, rewards(5));
        IntStream.rangeClosed(2, 6).forEach(players -> assertTrue(LongStream.of(rewards(players)).sum() <= players * 100L));
        assertEquals(0, calculator.reward(1, 6, 0));
    }

    private int[] deltas(int players) {
        return IntStream.rangeClosed(1, players).map(place -> calculator.delta(place, players)).toArray();
    }

    private long[] rewards(int players) {
        return IntStream.rangeClosed(1, players).mapToLong(place -> calculator.reward(place, players, 100)).toArray();
    }
}
