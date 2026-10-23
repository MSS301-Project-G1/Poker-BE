package com.msspoker.rankingservice.dto.event;

import com.msspoker.rankingservice.enums.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload of game-service match.finished (SPEC §6.2). Unknown fields are ignored. */
public record MatchFinishedEvent(UUID matchId, UUID tableId, GameMode mode, UUID sourceId, long entryFee,
                                 List<PlacementEvent> placements, Instant startedAt, Instant finishedAt) {
}
