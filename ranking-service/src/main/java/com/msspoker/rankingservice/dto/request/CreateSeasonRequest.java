package com.msspoker.rankingservice.dto.request;

import com.msspoker.rankingservice.constant.RankingConstants;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** resetPercent is the share of Elo kept when this season ends; rewards may be empty and set later. */
public record CreateSeasonRequest(@NotBlank @Size(max = RankingConstants.MAX_NAME_LENGTH) String name,
                                  @NotNull Instant startAt, @NotNull Instant endAt,
                                  @NotNull @Min(0) @Max(100) Integer resetPercent,
                                  @Valid List<@NotNull TierRewardRequest> rewards) {
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu.")
    public boolean isPeriodValid() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }
}
