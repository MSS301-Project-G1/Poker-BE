package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HandEvaluator {
    private static final int NO_RANK = 0;

    private HandEvaluator() {
    }

    public static HandRank bestOfSeven(List<Card> cards) {
        if (cards == null || cards.size() != PokerConstants.CARDS_IN_SHOWDOWN_HAND) {
            throw GameExceptions.invalidShowdownHand();
        }

        int[] rankCounts = new int[Rank.ACE.value() + 1];
        int[][] suitedRankCounts = new int[Suit.values().length][Rank.ACE.value() + 1];
        int[] suitCounts = new int[Suit.values().length];
        Set<Card> seen = new HashSet<>();
        for (Card card : cards) {
            if (card == null || !seen.add(card)) {
                throw GameExceptions.invalidShowdownHand();
            }
            int rank = card.rank().value();
            int suit = card.suit().ordinal();
            rankCounts[rank]++;
            suitedRankCounts[suit][rank]++;
            suitCounts[suit]++;
        }

        int flushSuit = -1;
        for (Suit suit : Suit.values()) {
            if (suitCounts[suit.ordinal()] >= PokerConstants.CARDS_IN_RANKED_HAND) {
                flushSuit = suit.ordinal();
                break;
            }
        }
        if (flushSuit >= 0) {
            int straightFlushHigh = straightHigh(suitedRankCounts[flushSuit]);
            if (straightFlushHigh != NO_RANK) {
                return new HandRank(HandCategory.STRAIGHT_FLUSH, List.of(straightFlushHigh));
            }
        }

        int four = highestWithAtLeastCount(rankCounts, 4);
        if (four != NO_RANK) {
            return new HandRank(HandCategory.FOUR_OF_A_KIND,
                    List.of(four, highestWithAtLeastCount(rankCounts, 1, four)));
        }

        int three = highestWithAtLeastCount(rankCounts, 3);
        int fullHousePair = highestWithAtLeastCount(rankCounts, 2, three);
        if (three != NO_RANK && fullHousePair != NO_RANK) {
            return new HandRank(HandCategory.FULL_HOUSE, List.of(three, fullHousePair));
        }

        if (flushSuit >= 0) {
            return new HandRank(HandCategory.FLUSH,
                    highestRanks(suitedRankCounts[flushSuit], PokerConstants.CARDS_IN_RANKED_HAND));
        }

        int straightHigh = straightHigh(rankCounts);
        if (straightHigh != NO_RANK) {
            return new HandRank(HandCategory.STRAIGHT, List.of(straightHigh));
        }

        if (three != NO_RANK) {
            return withKickers(HandCategory.THREE_OF_A_KIND, three,
                    highestRanks(rankCounts, 2, three));
        }

        int firstPair = highestWithAtLeastCount(rankCounts, 2);
        int secondPair = highestWithAtLeastCount(rankCounts, 2, firstPair);
        if (secondPair != NO_RANK) {
            return new HandRank(HandCategory.TWO_PAIR,
                    List.of(firstPair, secondPair,
                            highestWithAtLeastCount(rankCounts, 1, firstPair, secondPair)));
        }
        if (firstPair != NO_RANK) {
            return withKickers(HandCategory.ONE_PAIR, firstPair,
                    highestRanks(rankCounts, 3, firstPair));
        }
        return new HandRank(HandCategory.HIGH_CARD,
                highestRanks(rankCounts, PokerConstants.CARDS_IN_RANKED_HAND));
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

    private static HandRank withKickers(HandCategory category, int mainRank, List<Integer> kickers) {
        List<Integer> tiebreakers = new ArrayList<>();
        tiebreakers.add(mainRank);
        tiebreakers.addAll(kickers);
        return new HandRank(category, tiebreakers);
    }

    private static int highestWithAtLeastCount(int[] counts, int minimumCount, int... excludedRanks) {
        for (int rank = Rank.ACE.value(); rank >= Rank.TWO.value(); rank--) {
            if (counts[rank] >= minimumCount && !isExcluded(rank, excludedRanks)) {
                return rank;
            }
        }
        return NO_RANK;
    }

    private static List<Integer> highestRanks(int[] counts, int limit, int... excludedRanks) {
        List<Integer> ranks = new ArrayList<>();
        for (int rank = Rank.ACE.value(); rank >= Rank.TWO.value() && ranks.size() < limit; rank--) {
            if (counts[rank] > 0 && !isExcluded(rank, excludedRanks)) {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    private static boolean isExcluded(int rank, int[] excludedRanks) {
        for (int excluded : excludedRanks) {
            if (rank == excluded) {
                return true;
            }
        }
        return false;
    }
}
