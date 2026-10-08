package com.msspoker.gameservice.service.poker;

import com.msspoker.gameservice.enums.HandCategory;
import com.msspoker.gameservice.model.poker.HandRank;
import com.msspoker.gameservice.model.poker.PotAward;
import com.msspoker.gameservice.model.poker.PotContribution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotDistributorTest {
    @Test
    void sidePotsAndUncalledBetAreAssignedToTheRightPlayers() {
        var result = PotDistributor.distribute(List.of(
                new PotContribution(0, 100, false),
                new PotContribution(1, 50, false),
                new PotContribution(2, 20, false)),
                Map.of(0, highCard(2), 1, highCard(10), 2, highCard(14)), 0, 3);

        assertEquals(Map.of(2, 60L, 1, 60L), result.winnings());
        assertEquals(Map.of(0, 50L), result.refunds());
        assertEquals(List.of(60L, 60L), result.pots().stream()
                .map(PotAward::amount).toList());
    }

    @Test
    void soleRemainingPlayerWinsContestedChipsAndGetsOwnExcessBack() {
        var result = PotDistributor.distribute(List.of(
                new PotContribution(0, 100, false),
                new PotContribution(1, 30, true),
                new PotContribution(2, 20, true)), Map.of(), 2, 3);

        assertEquals(Map.of(0, 80L), result.winnings());
        assertEquals(Map.of(0, 70L), result.refunds());
    }

    @Test
    void shortBigBlindCanWinOnlyTheMainPotWhenOthersCallTheFullBlind() {
        var result = PotDistributor.distribute(List.of(
                new PotContribution(0, 20, false),
                new PotContribution(1, 5, false),
                new PotContribution(2, 20, false)),
                Map.of(0, highCard(10), 1, highCard(14), 2, highCard(2)), 0, 3);

        assertEquals(Map.of(1, 15L, 0, 30L), result.winnings());
        assertEquals(Map.of(), result.refunds());
    }

    @Test
    void tiedPotGivesOddChipToWinnerNearestLeftOfDealer() {
        var result = PotDistributor.distribute(List.of(
                new PotContribution(0, 1, false),
                new PotContribution(1, 1, false),
                new PotContribution(3, 1, false)),
                Map.of(0, highCard(2), 1, highCard(14), 3, highCard(14)), 0, 5);

        assertEquals(Map.of(1, 2L, 3, 1L), result.winnings());
        assertEquals(List.of(1, 3), result.pots().getFirst().winnerSeats());
    }

    @Test
    void thousandsOfPotsKeepAllCommittedChips() {
        Random random = new Random(98765L);
        for (int game = 0; game < 2_000; game++) {
            long a = random.nextInt(100) + 1;
            long b = random.nextInt(100) + 1;
            long c = random.nextInt(100) + 1;
            var result = PotDistributor.distribute(List.of(
                    new PotContribution(0, a, false),
                    new PotContribution(1, b, false),
                    new PotContribution(2, c, false)),
                    Map.of(0, highCard(2), 1, highCard(10), 2, highCard(14)), 0, 3);
            long paid = result.winnings().values().stream().mapToLong(Long::longValue).sum()
                    + result.refunds().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(a + b + c, paid);
        }
    }

    @Test
    void duplicateSeatsAndMissingShowdownHandsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> PotDistributor.distribute(List.of(
                new PotContribution(0, 10, false),
                new PotContribution(0, 10, false)), Map.of(), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> PotDistributor.distribute(List.of(
                new PotContribution(0, 10, false),
                new PotContribution(1, 10, false)), Map.of(0, highCard(14)), 0, 2));
    }

    private static HandRank highCard(int rank) {
        return new HandRank(HandCategory.HIGH_CARD, List.of(rank));
    }
}
