package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class HandEvaluator {
    private HandEvaluator() {
    }

    public static HandRank bestOfSeven(List<Card> cards) {
        if (cards == null || cards.size() != PokerConstants.CARDS_IN_SHOWDOWN_HAND
                || cards.contains(null) || new HashSet<>(cards).size() != PokerConstants.CARDS_IN_SHOWDOWN_HAND) {
            throw GameExceptions.invalidShowdownHand();
        }

        HandRank best = null;
        int[] selected = new int[PokerConstants.CARDS_IN_RANKED_HAND];
        for (int i = 0; i < selected.length; i++) {
            selected[i] = i;
        }
        while (true) {
            List<Card> fiveCards = new ArrayList<>(PokerConstants.CARDS_IN_RANKED_HAND);
            for (int index : selected) {
                fiveCards.add(cards.get(index));
            }
            HandRank candidate = evaluateFive(fiveCards);
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }

            int index = selected.length - 1;
            while (index >= 0 && selected[index] == cards.size() - selected.length + index) {
                index--;
            }
            if (index < 0) {
                return best;
            }
            selected[index]++;
            for (int following = index + 1; following < selected.length; following++) {
                selected[following] = selected[following - 1] + 1;
            }
        }
    }

    private static HandRank evaluateFive(List<Card> cards) {
        int[] counts = new int[Rank.ACE.value() + 1];
        for (Card card : cards) {
            counts[card.rank().value()]++;
        }

        boolean flush = cards.stream().allMatch(card -> card.suit() == cards.getFirst().suit());
        int straightHigh = straightHigh(counts);
        if (flush && straightHigh != 0) {
            return new HandRank(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh));
        }

        int four = highestWithCount(counts, 4);
        if (four != 0) {
            return new HandRank(HandCategory.FOUR_OF_A_KIND,
                    List.of(four, highestWithCount(counts, 1)));
        }

        int three = highestWithCount(counts, 3);
        int pair = highestWithCount(counts, 2);
        if (three != 0 && pair != 0) {
            return new HandRank(HandCategory.FULL_HOUSE, List.of(three, pair));
        }
        if (flush) {
            return new HandRank(HandCategory.FLUSH, ranksWithCount(counts, 1));
        }
        if (straightHigh != 0) {
            return new HandRank(HandCategory.STRAIGHT, List.of(straightHigh));
        }
        if (three != 0) {
            List<Integer> tiebreakers = new ArrayList<>();
            tiebreakers.add(three);
            tiebreakers.addAll(ranksWithCount(counts, 1));
            return new HandRank(HandCategory.THREE_OF_A_KIND, tiebreakers);
        }

        List<Integer> pairs = ranksWithCount(counts, 2);
        if (pairs.size() == 2) {
            return new HandRank(HandCategory.TWO_PAIR,
                    List.of(pairs.get(0), pairs.get(1), highestWithCount(counts, 1)));
        }
        if (pair != 0) {
            List<Integer> tiebreakers = new ArrayList<>();
            tiebreakers.add(pair);
            tiebreakers.addAll(ranksWithCount(counts, 1));
            return new HandRank(HandCategory.ONE_PAIR, tiebreakers);
        }
        return new HandRank(HandCategory.HIGH_CARD, ranksWithCount(counts, 1));
    }

    private static int straightHigh(int[] counts) {
        for (int high = Rank.ACE.value(); high >= Rank.SIX.value(); high--) {
            boolean straight = true;
            for (int rank = high; rank > high - PokerConstants.CARDS_IN_RANKED_HAND; rank--) {
                if (counts[rank] == 0) {
                    straight = false;
                    break;
                }
            }
            if (straight) {
                return high;
            }
        }
        for (int rank = Rank.TWO.value(); rank <= Rank.FIVE.value(); rank++) {
            if (counts[rank] == 0) {
                return 0;
            }
        }
        return counts[Rank.ACE.value()] > 0 ? Rank.FIVE.value() : 0;
    }

    private static int highestWithCount(int[] counts, int requiredCount) {
        for (int rank = Rank.ACE.value(); rank >= Rank.TWO.value(); rank--) {
            if (counts[rank] == requiredCount) {
                return rank;
            }
        }
        return 0;
    }

    private static List<Integer> ranksWithCount(int[] counts, int requiredCount) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = Rank.ACE.value(); rank >= Rank.TWO.value(); rank--) {
            if (counts[rank] == requiredCount) {
                ranks.add(rank);
            }
        }
        return ranks;
    }
}
