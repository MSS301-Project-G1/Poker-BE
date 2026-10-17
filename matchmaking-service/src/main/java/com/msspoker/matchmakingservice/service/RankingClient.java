package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.response.PlayerRankingResponse;

import java.util.UUID;

public interface RankingClient {
    PlayerRankingResponse ranking(UUID accountId);
}
