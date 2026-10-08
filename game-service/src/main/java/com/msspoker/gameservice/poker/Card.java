package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

public record Card(Rank rank, Suit suit) {
    public Card {
        if (rank == null || suit == null) {
            throw GameExceptions.invalidCard();
        }
    }
}
