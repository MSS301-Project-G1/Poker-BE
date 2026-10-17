package com.msspoker.competitionservice.dto.response;

import java.util.UUID;

public record TournamentSeatResponse(UUID accountId, int seatIndex, Integer place) {
}
