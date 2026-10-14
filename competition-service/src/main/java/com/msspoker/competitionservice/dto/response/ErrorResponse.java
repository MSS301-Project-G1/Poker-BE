package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.CompetitionError;

import java.time.Instant;

public record ErrorResponse(CompetitionError code, String message, Instant timestamp, String requestId) {
}
