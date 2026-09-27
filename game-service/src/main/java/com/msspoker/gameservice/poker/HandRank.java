package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

import java.util.List;

public record HandRank(HandCategory category, List<Integer> tiebreakers) implements Comparable<HandRank> {
    public HandRank {
        if (category == null || tiebreakers == null || tiebreakers.isEmpty()
                || tiebreakers.stream().anyMatch(value -> value == null
                || value < Rank.TWO.value() || value > Rank.ACE.value())) {
            throw GameExceptions.invalidHandRank();
        }
        tiebreakers = List.copyOf(tiebreakers);
    }

    @Override
    public int compareTo(HandRank other) {
        int categoryComparison = Integer.compare(category.ordinal(), other.category.ordinal());
        if (categoryComparison != 0) {
            return categoryComparison;
        }
        for (int i = 0; i < Math.min(tiebreakers.size(), other.tiebreakers.size()); i++) {
            int comparison = Integer.compare(tiebreakers.get(i), other.tiebreakers.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(tiebreakers.size(), other.tiebreakers.size());
    }
}
