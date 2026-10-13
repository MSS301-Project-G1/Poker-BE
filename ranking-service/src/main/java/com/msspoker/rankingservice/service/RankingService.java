package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;
import com.msspoker.rankingservice.dto.response.PlayerRankingResponse;
import com.msspoker.rankingservice.dto.response.RankedPlayerResponse;

import java.util.List;
import java.util.UUID;

public interface RankingService {
    PlayerRankingResponse ranking(UUID accountId);

    List<RankedPlayerResponse> top(int limit);

    PageResponse<RankedPlayerResponse> leaderboard(int page, int size);

    MyRankingResponse me(UUID accountId);
}
