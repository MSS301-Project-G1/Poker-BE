package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.RankingError;

import java.time.Instant;

public record ErrorResponse(RankingError code, String message, Instant timestamp, String requestId) {
}
