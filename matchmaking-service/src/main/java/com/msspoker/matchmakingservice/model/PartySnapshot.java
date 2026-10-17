package com.msspoker.matchmakingservice.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PartySnapshot(
        UUID partyId,
        String partyCode,
        UUID leaderId,
        List<UUID> memberIds,
        Instant createdAt
) {
}
