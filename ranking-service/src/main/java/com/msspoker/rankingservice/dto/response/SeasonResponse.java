package com.msspoker.rankingservice.dto.response;

import com.msspoker.rankingservice.enums.SeasonStatus;

import java.time.Instant;
import java.util.UUID;

public record SeasonResponse(UUID id, String name, Instant startAt, Instant endAt, SeasonStatus status, int resetPercent) {
}
