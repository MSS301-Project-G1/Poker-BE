package com.msspoker.gameservice.engine;

public record TableRules(long smallBlind, long bigBlind, long startingChips, int turnTimeSeconds,
                         int minPlayers, int maxPlayers) {
    public static final int HOLE_CARDS = 2;
    public static final int BOARD_CARDS = 5;
    public static final int FLOP_CARDS = 3;
    public static final int MAX_SEATS = 10;
    public static final int NO_SEAT = -1;
    public static final long NOT_ACTED = -1;
    public static final long MAX_CHIPS = 1_000_000_000_000L;

    public TableRules {
        if (smallBlind <= 0 || bigBlind <= smallBlind || startingChips < bigBlind
                || startingChips > MAX_CHIPS || bigBlind > MAX_CHIPS
                || turnTimeSeconds <= 0 || minPlayers < HOLE_CARDS
                || maxPlayers < minPlayers || maxPlayers > MAX_SEATS) {
            throw com.msspoker.gameservice.exception.GameExceptions.invalidRules();
        }
    }
}
