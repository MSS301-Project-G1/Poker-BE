package com.msspoker.gameservice.realtime;

import com.msspoker.gameservice.api.GameDtos.Placement;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.poker.Card;
import com.msspoker.gameservice.poker.HandRank;
import com.msspoker.gameservice.poker.PotDistributor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameSnapshot(UUID tableId, UUID matchId, GameMode mode, UUID accountId,
                           Street street, int handNumber, long actionSequence,
                           int dealerSeat, int smallBlindSeat, int bigBlindSeat, int actorSeat,
                           List<Card> board, long pot, long currentBet, Instant deadline, Instant serverTime,
                           List<Player> seats, List<ActionType> legalActions, long callAmount,
                           long minRaiseTo, long maxRaiseTo, List<Placement> placements,
                           PotDistributor.Distribution distribution) {
    public record Player(UUID accountId, int seatIndex, long chips, long streetBet,
                         boolean folded, boolean eliminated, boolean leftEarly,
                         int place, String lastAction, int holeCardCount, List<Card> cards, HandRank handRank) {
    }
}
