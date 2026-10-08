package com.msspoker.gameservice.poker;

import com.msspoker.gameservice.exception.GameExceptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class Deck {
    private final List<Card> cards = new ArrayList<>(PokerConstants.CARDS_IN_DECK);
    private int nextCard;

    public Deck(long seed) {
        this(new Random(seed));
    }

    public Deck(RandomGenerator random) {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, random);
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
