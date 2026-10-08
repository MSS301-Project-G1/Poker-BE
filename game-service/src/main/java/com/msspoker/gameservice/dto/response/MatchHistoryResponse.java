package com.msspoker.gameservice.dto.response;

import com.msspoker.gameservice.enums.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchHistoryResponse(UUID id, UUID tableId, GameMode mode, UUID sourceId, long entryFee,
                        String status, Instant startedAt, Instant finishedAt,
                        List<PlacementResponse> placements) {
}
