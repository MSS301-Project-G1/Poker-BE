package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.TournamentStatus;
import com.msspoker.competitionservice.enums.TournamentType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tournament detail with entries and the bracket (final table first, then qualifiers). prizes are the
 * coins of final places 1, 2, 3 for the current prizePool; the champion also receives rewardProductId.
 */
public record TournamentResponse(UUID id, String name, TournamentType type, TournamentStatus status, Instant startAt,
                                 long entryFee, int slots, long rewardCoins, UUID rewardProductId, long prizePool,
                                 List<Long> prizes, List<TournamentEntryResponse> entries,
                                 List<TournamentTableResponse> tables) {
}
