package com.msspoker.authservice.dto.auth.response;

import com.msspoker.authservice.enums.AccountRole;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(UUID accountId, String email, AccountRole role,
        Instant accessExpiresAt, Instant refreshExpiresAt) {
}
