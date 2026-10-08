package com.msspoker.gameservice.dto.event;

import com.msspoker.gameservice.dto.response.PlacementResponse;
import com.msspoker.gameservice.enums.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchFinishedEvent(UUID matchId, UUID tableId, GameMode mode, UUID sourceId, long entryFee,
                       List<PlacementResponse> placements, Instant startedAt, Instant finishedAt) {
}
