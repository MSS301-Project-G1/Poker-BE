package com.msspoker.authservice.dto.admin.response;

import com.msspoker.authservice.enums.AccountModerationAction;

import java.time.Instant;
import java.util.UUID;

public record AccountModerationEventResponse(UUID id, UUID accountId, UUID actorId, AccountModerationAction action,
        String reason, String previousValue, Instant occurredAt) {
}
