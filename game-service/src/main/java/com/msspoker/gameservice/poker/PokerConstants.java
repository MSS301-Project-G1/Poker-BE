package com.msspoker.gameservice.poker;

final class PokerConstants {
    static final int CARDS_IN_DECK = Rank.values().length * Suit.values().length;
    static final int CARDS_IN_SHOWDOWN_HAND = 7;
    static final int CARDS_IN_RANKED_HAND = 5;
    static final int MIN_TABLE_SEATS = 2;

    private PokerConstants() {
    }
}
