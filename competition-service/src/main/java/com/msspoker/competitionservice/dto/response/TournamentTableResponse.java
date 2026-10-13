package com.msspoker.competitionservice.dto.response;

import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;

import java.util.List;
import java.util.UUID;

/** One bracket table; tableId is the game table to open once the status is PLAYING. */
public record TournamentTableResponse(UUID id, TournamentRound round, int tableIndex, TableStatus status, UUID tableId,
                                      List<TournamentSeatResponse> seats) {
}
