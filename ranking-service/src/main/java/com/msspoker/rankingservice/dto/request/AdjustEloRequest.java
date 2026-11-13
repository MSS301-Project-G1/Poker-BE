package com.msspoker.rankingservice.dto.request;

import com.msspoker.rankingservice.constant.RankingConstants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The new active-season Elo of a player and why an admin sets it. */
public record AdjustEloRequest(@NotNull @Min(0) @Max(RankingConstants.MAX_ELO) Integer elo,
                               @NotBlank @Size(max = RankingConstants.MAX_REASON_LENGTH) String reason) {
}
