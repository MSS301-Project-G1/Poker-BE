package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.request.CreateSeasonRequest;
import com.msspoker.rankingservice.dto.request.UpdateSeasonRewardsRequest;
import com.msspoker.rankingservice.dto.response.SeasonDetailResponse;
import com.msspoker.rankingservice.dto.response.SeasonResponse;
import com.msspoker.rankingservice.dto.response.TierRewardResponse;
import com.msspoker.rankingservice.entity.SeasonEntity;

import java.util.List;
import java.util.UUID;

public interface SeasonService {
    SeasonEntity active();

    /** Active season locked until the caller's transaction ends; serializes Elo updates with closing. */
    SeasonEntity lockActive();

    SeasonResponse current();

    List<TierRewardResponse> rewards(UUID seasonId);

    List<SeasonDetailResponse> list();

    SeasonDetailResponse create(CreateSeasonRequest request);

    SeasonDetailResponse updateRewards(UUID seasonId, UpdateSeasonRewardsRequest request);

    /** Ends the active season now and activates the next one; returns the ended season. */
    SeasonDetailResponse close();

    /** Scheduled: closes a season past its end when the next one exists, or activates a due season. */
    void closeDueSeason();
}
