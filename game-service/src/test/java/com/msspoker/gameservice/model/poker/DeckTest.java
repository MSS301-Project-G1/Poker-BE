package com.msspoker.gameservice.model.poker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeckTest {
    @Test
    void aSeedReproducesAllFiftyTwoUniqueCards() {
        Deck first = new Deck(12345L);
        Deck sameSeed = new Deck(12345L);
        Deck otherSeed = new Deck(54321L);

        List<Card> firstDraw = drawAll(first);
        assertEquals(52, firstDraw.size());
        assertEquals(52, new HashSet<>(firstDraw).size());
        assertEquals(firstDraw, drawAll(sameSeed));
        assertNotEquals(firstDraw, drawAll(otherSeed));
        assertEquals(0, first.remaining());
        assertThrows(NoSuchElementException.class, first::draw);
    }

    private static List<Card> drawAll(Deck deck) {
        List<Card> cards = new ArrayList<>();
        while (deck.remaining() > 0) {
            cards.add(deck.draw());
        }
        return cards;
    }
}
