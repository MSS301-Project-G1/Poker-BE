package com.msspoker.matchmakingservice.dto.response;

import java.time.Instant;

public record AccountRestrictionsResponse(
        boolean banned,
        Instant chatBannedUntil,
        Instant rankBannedUntil
) {
}
