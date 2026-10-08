package com.msspoker.gameservice.constant;

import com.msspoker.gameservice.enums.Rank;
import com.msspoker.gameservice.enums.Suit;

public final class PokerConstants {
    public static final int CARDS_IN_DECK = Rank.values().length * Suit.values().length;
    public static final int CARDS_IN_SHOWDOWN_HAND = 7;
    public static final int CARDS_IN_RANKED_HAND = 5;
    public static final int MIN_TABLE_SEATS = 2;

    private PokerConstants() {
    }
}
