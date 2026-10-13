package com.msspoker.competitionservice.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RoomMemberResponse(UUID accountId, boolean ready, boolean owner, Instant joinedAt) {
}
