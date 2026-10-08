package com.msspoker.gameservice.dto.response;

import com.msspoker.gameservice.model.poker.Card;
import com.msspoker.gameservice.model.poker.HandRank;

import java.util.List;
import java.util.UUID;

public record PlayerSnapshotResponse(UUID accountId, int seatIndex, long chips, long streetBet,
                                     boolean folded, boolean eliminated, boolean leftEarly,
                                     int place, String lastAction, int holeCardCount,
                                     List<Card> cards, HandRank handRank) {
}
