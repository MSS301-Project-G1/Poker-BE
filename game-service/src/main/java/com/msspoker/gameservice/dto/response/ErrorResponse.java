package com.msspoker.gameservice.dto.response;

import com.msspoker.gameservice.enums.GameError;

import java.time.Instant;

public record ErrorResponse(GameError code, String message, Instant timestamp, String requestId) {
}
