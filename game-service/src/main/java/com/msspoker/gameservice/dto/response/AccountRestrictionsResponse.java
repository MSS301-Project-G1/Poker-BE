package com.msspoker.gameservice.dto.response;

import java.time.Instant;

/** auth-service GET /internal/accounts/{accountId}/restrictions (SPEC §6.1), raw JSON without an envelope. */
public record AccountRestrictionsResponse(boolean banned, Instant chatBannedUntil, Instant rankBannedUntil) {
}
