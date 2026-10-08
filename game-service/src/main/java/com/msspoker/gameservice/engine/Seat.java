package com.msspoker.gameservice.engine;

import com.msspoker.gameservice.poker.Card;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class Seat {
    private final UUID accountId;
    private final int seatIndex;
    long chips;
    long streetBet;
    long contribution;
    long handStartingChips;
    long lastActedBet = TableRules.NOT_ACTED;
    boolean folded;
    boolean eliminated;
    boolean leftEarly;
    int place;
    int consecutiveTimeouts;
    String lastAction;
    final List<Card> holeCards = new ArrayList<>(TableRules.HOLE_CARDS);

    public boolean canAct() {
        return !eliminated && !folded && chips > 0;
    }
}
