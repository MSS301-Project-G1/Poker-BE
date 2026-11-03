package com.msspoker.authservice.dto.admin.response;

import com.msspoker.authservice.enums.AccountBanAction;

import java.time.Instant;
import java.util.UUID;

public record AccountBanEventResponse(UUID id, UUID accountId, UUID actorId, AccountBanAction action,
        String reason, Instant until, Instant occurredAt) {
}
