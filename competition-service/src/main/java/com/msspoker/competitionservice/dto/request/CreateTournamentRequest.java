package com.msspoker.competitionservice.dto.request;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.enums.TournamentType;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/** Optional fields are boxed so they may be omitted: slots defaults to the configured field size (32). */
public record CreateTournamentRequest(@NotBlank @Size(max = CompetitionConstants.MAX_NAME_LENGTH) String name,
                                      @NotNull TournamentType type, @NotNull @Future Instant startAt,
                                      @PositiveOrZero long entryFee, @Min(2) Integer slots,
                                      @PositiveOrZero Long rewardCoins, UUID rewardProductId) {
    @AssertTrue(message = "Giải Cao thủ không thu phí vào.")
    public boolean isEliteFree() {
        return type != TournamentType.ELITE || entryFee == 0;
    }
}
