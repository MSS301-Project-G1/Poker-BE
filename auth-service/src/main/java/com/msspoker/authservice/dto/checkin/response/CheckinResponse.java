package com.msspoker.authservice.dto.checkin.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.msspoker.authservice.enums.CheckinRewardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckinResponse {
    private UUID checkinId;
    private LocalDate date;
    private long rewardCoins;
    private CheckinRewardStatus rewardStatus;
    private Instant checkedInAt;
    private Instant rewardedAt;

    @JsonProperty("isMakeup")
    private boolean makeup;
}
