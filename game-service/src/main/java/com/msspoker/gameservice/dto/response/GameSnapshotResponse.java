package com.msspoker.gameservice.dto.response;

import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.enums.Street;
import com.msspoker.gameservice.model.poker.Card;
import com.msspoker.gameservice.model.poker.PotDistribution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameSnapshotResponse(UUID tableId, UUID matchId, GameMode mode, UUID accountId,
                           Street street, int handNumber, long actionSequence,
                           int dealerSeat, int smallBlindSeat, int bigBlindSeat, int actorSeat,
                           List<Card> board, long pot, long currentBet, Instant deadline, Instant serverTime,
                           List<PlayerSnapshotResponse> seats, List<ActionType> legalActions, long callAmount,
                           long minRaiseTo, long maxRaiseTo, List<PlacementResponse> placements,
                           PotDistribution distribution) {
}
