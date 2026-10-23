package com.msspoker.authservice.dto.auth.response;

import java.time.Instant;

public record WebSocketTokenResponse(String accessToken, Instant expiresAt) {
}
