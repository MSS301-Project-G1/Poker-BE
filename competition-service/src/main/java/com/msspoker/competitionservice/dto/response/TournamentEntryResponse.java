package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.EntryStatus;

import java.time.Instant;
import java.util.UUID;

/** confirmBy is set for INVITED entries only: the time the ELITE invitation expires. */
public record TournamentEntryResponse(UUID accountId, EntryStatus status, Integer finalPlace, Instant registeredAt,
                                      Instant confirmBy) {
}
