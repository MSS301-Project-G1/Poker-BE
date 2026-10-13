package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.response.SeasonRewardResponse;

import java.util.List;
import java.util.UUID;

public interface SeasonRewardService {
    /** Ended seasons where the player reached a rewarded tier, claimed or not. */
    List<SeasonRewardResponse> mine(UUID accountId);

    /** Idempotent: claiming again returns the existing claim. */
    SeasonRewardResponse claim(UUID seasonId, UUID accountId);
}
