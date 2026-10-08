package com.msspoker.gameservice.model.poker;

import com.msspoker.gameservice.enums.Rank;
import com.msspoker.gameservice.enums.Suit;
import com.msspoker.gameservice.exception.GameExceptions;

public record Card(Rank rank, Suit suit) {
    public Card {
        if (rank == null || suit == null) {
            throw GameExceptions.invalidCard();
        }
    }
}
