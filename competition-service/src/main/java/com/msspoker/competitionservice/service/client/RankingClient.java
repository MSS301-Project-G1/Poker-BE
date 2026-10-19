package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;

import java.util.List;

public interface RankingClient {
    /** Top players of the active season, best first; throws COMP_DEPENDENCY_UNAVAILABLE on failure. */
    List<RankedPlayerResponse> top(int limit);
}
