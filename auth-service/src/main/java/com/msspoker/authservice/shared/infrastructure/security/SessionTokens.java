package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.dto.auth.response.SessionResponse;

// Internal result only. HTTP responses expose metadata, credentials go into HttpOnly cookies.
public record SessionTokens(String accessToken, String refreshToken, SessionResponse response) {
    @Override
    public String toString() {
        return "SessionTokens[credentials=REDACTED]";
    }
}
