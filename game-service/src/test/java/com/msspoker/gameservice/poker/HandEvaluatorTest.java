package com.msspoker.gameservice.poker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {
    @Test
    void choosesTheBestFiveOfSevenForEveryCategory() {
        assertEquals(new HandRank(HandCategory.STRAIGHT_FLUSH, List.of(14)),
                hand("As", "Ks", "Qs", "Js", "Ts", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.FOUR_OF_A_KIND, List.of(9, 14)),
                hand("9s", "9h", "9d", "9c", "As", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.FULL_HOUSE, List.of(14, 13)),
                hand("As", "Ah", "Ad", "Ks", "Kh", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.FLUSH, List.of(14, 13, 9, 6, 2)),
                hand("As", "Ks", "9s", "6s", "2s", "Jd", "3c"));
        assertEquals(new HandRank(HandCategory.STRAIGHT, List.of(9)),
                hand("9s", "8h", "7d", "6c", "5s", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.THREE_OF_A_KIND, List.of(14, 13, 12)),
                hand("As", "Ah", "Ad", "Ks", "Qh", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.TWO_PAIR, List.of(14, 13, 12)),
                hand("As", "Ah", "Ks", "Kh", "Qh", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.ONE_PAIR, List.of(14, 13, 12, 11)),
                hand("As", "Ah", "Ks", "Qh", "Jd", "2d", "3c"));
        assertEquals(new HandRank(HandCategory.HIGH_CARD, List.of(14, 13, 12, 11, 9)),
                hand("As", "Kh", "Qd", "Jc", "9s", "2d", "3c"));
    }

    @Test
    void aceLowStraightIsFiveHighAndLosesToSixHigh() {
        HandRank wheel = hand("As", "2h", "3d", "4c", "5s", "Kd", "Qc");
        HandRank sixHigh = hand("2s", "3h", "4d", "5c", "6s", "Kd", "Qc");

        assertEquals(new HandRank(HandCategory.STRAIGHT, List.of(5)), wheel);
        assertTrue(sixHigh.compareTo(wheel) > 0);
    }

    @Test
    void twoThroughFiveWithoutAnAceIsNotASevenCardStraight() {
        assertEquals(HandCategory.HIGH_CARD,
                hand("2s", "3h", "4d", "5c", "Ks", "Qd", "9c").category());
    }

    @Test
    void twoTripletsMakeTheHighestAvailableFullHouse() {
        assertEquals(new HandRank(HandCategory.FULL_HOUSE, List.of(14, 13)),
                hand("As", "Ah", "Ad", "Ks", "Kh", "Kd", "2c"));
    }

    @Test
    void flushUsesItsBestFiveSuitedCardsEvenWhenAnotherSuitCompletesAStraight() {
        assertEquals(new HandRank(HandCategory.FLUSH, List.of(14, 13, 12, 11, 9)),
                hand("As", "Ks", "Qs", "Js", "9s", "Td", "2c"));
    }

    @Test
    void holeCardsDoNotBreakATieWhenTheBoardAlreadyHasTheBestHand() {
        HandRank first = hand("As", "Ks", "Qs", "Js", "Ts", "2d", "3c");
        HandRank second = hand("As", "Ks", "Qs", "Js", "Ts", "4d", "5c");

        assertEquals(first, second);
    }

    @Test
    void oneHoleCardCanBeAKickerWhenItIsAmongTheBestFive() {
        HandRank jackKicker = hand("As", "Ah", "Kd", "Qc", "2s", "Jd", "3c");
        HandRank tenKicker = hand("As", "Ah", "Kd", "Qc", "2s", "Td", "4c");

        assertTrue(jackKicker.compareTo(tenKicker) > 0);
    }

    @Test
    void kickerAndFullHousePairBreakTies() {
        assertTrue(hand("As", "Ah", "Ks", "Qh", "Jd", "2d", "3c")
                .compareTo(hand("Ac", "Ad", "Qs", "Jh", "Td", "2s", "3d")) > 0);
        assertTrue(hand("As", "Ah", "Ad", "Ks", "Kh", "2d", "3c")
                .compareTo(hand("Ac", "Ah", "Ad", "Qs", "Qh", "2d", "3c")) > 0);
    }

    @Test
    void invalidHandsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> hand("As", "As", "3d", "4c", "5s", "Kd", "Qc"));
        assertThrows(IllegalArgumentException.class,
                () -> hand("As", "2h", "3d", "4c", "5s", "Kd"));
    }

    private static HandRank hand(String... cards) {
        List<Card> parsed = Arrays.stream(cards).map(HandEvaluatorTest::card).toList();
        return HandEvaluator.bestOfSeven(parsed);
    }

    private static Card card(String notation) {
        String ranks = "23456789TJQKA";
        String suits = "cdhs";
        return new Card(Rank.values()[ranks.indexOf(notation.charAt(0))],
                Suit.values()[suits.indexOf(notation.charAt(1))]);
    }
}
