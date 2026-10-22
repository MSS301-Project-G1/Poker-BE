package com.msspoker.authservice.dto.auth.response;

import java.time.Instant;

public record AccountRestrictionsResponse(boolean banned, Instant chatBannedUntil, Instant rankBannedUntil) { }
