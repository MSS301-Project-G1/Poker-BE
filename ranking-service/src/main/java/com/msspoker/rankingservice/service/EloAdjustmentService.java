package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.request.AdjustEloRequest;
import com.msspoker.rankingservice.dto.response.EloAdjustmentResponse;
import com.msspoker.rankingservice.dto.response.MyRankingResponse;
import com.msspoker.rankingservice.dto.response.PageResponse;

import java.util.UUID;

public interface EloAdjustmentService {
    /** Sets the player's active-season Elo and records who changed it and why; returns the new standing. */
    MyRankingResponse adjust(UUID adminId, UUID accountId, AdjustEloRequest request);

    /** Admin changes to the player's Elo across seasons, newest first. */
    PageResponse<EloAdjustmentResponse> history(UUID accountId, int page, int size);
}
