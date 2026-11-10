package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.event.MatchFinishedEvent;
import com.msspoker.competitionservice.dto.request.CreateTournamentRequest;
import com.msspoker.competitionservice.dto.response.PageResponse;
import com.msspoker.competitionservice.dto.response.TournamentResponse;
import com.msspoker.competitionservice.dto.response.TournamentSummaryResponse;

import java.util.UUID;

public interface TournamentService {
    TournamentResponse create(UUID adminId, CreateTournamentRequest request);

    /** Upcoming and running tournaments soonest first, or (past) finished and cancelled ones latest first. */
    PageResponse<TournamentSummaryResponse> list(boolean past, int page, int size);

    TournamentResponse get(UUID tournamentId);

    TournamentResponse register(UUID tournamentId, UUID accountId);

    /** ELITE invitee accepts the invitation before its deadline; repeating returns the same result. */
    TournamentResponse confirm(UUID tournamentId, UUID accountId);

    TournamentResponse start(UUID tournamentId);

    /** Scheduler: starts a tournament whose start time came, or cancels it with refunds when too few play. */
    void startDue(UUID tournamentId);

    TournamentResponse cancel(UUID tournamentId);

    void matchFinished(MatchFinishedEvent event);
}
