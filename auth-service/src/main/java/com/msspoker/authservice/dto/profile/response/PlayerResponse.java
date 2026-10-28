package com.msspoker.authservice.dto.profile.response;

import java.util.UUID;

/** Public identity used to find and select a player; contains no login information. */
public record PlayerResponse(UUID accountId, String playerCode, String displayName, String avatarUrl) {}
