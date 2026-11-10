package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.enums.Tier;

public interface EloCalculator {
    /** Elo change for a place at a table of the given size (SPEC §8). */
    int delta(int place, int players);

    int apply(int elo, int delta);

    Tier tier(int elo);

    Tier displayTier(Tier tier, long position);

    /** Places that gain Elo, up to ranking.win-streak-places, extend the win streak. */
    boolean extendsWinStreak(int place, int players);

    /** Coins for a place: fee multiples paid from the top while the table's entry fees cover them. */
    long reward(int place, int players, long entryFee);

    /** Elo kept into the next season: keepPercent of the final Elo, rounded down, never below the floor. */
    int carryOver(int elo, int keepPercent);
}
