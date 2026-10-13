package com.msspoker.rankingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Replaces every tier reward of the season. */
public record UpdateSeasonRewardsRequest(@NotNull @Valid List<@NotNull TierRewardRequest> rewards) {
}
