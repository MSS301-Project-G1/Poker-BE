package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;

import java.time.Instant;
import java.util.UUID;

/** entryCount excludes expired ELITE invitations. */
public record TournamentSummaryResponse(UUID id, String name, TournamentType type, TournamentStatus status,
                                        Instant startAt, long entryFee, int slots, int entryCount, long prizePool) {
}
