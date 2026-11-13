package com.msspoker.rankingservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record EloAdjustmentResponse(UUID id, UUID seasonId, UUID accountId, UUID actorId, int eloBefore, int eloAfter,
                                    String reason, Instant occurredAt) {
}
