package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Deck {
    private final List<Card> cards = new ArrayList<>(PokerConstants.CARDS_IN_DECK);
    private int nextCard;

    public Deck(long seed) {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, new Random(seed));
    }

    public Card draw() {
        if (nextCard == cards.size()) {
            throw GameExceptions.emptyDeck();
        }
        return cards.get(nextCard++);
    }

    public int remaining() {
        return cards.size() - nextCard;
    }
}
