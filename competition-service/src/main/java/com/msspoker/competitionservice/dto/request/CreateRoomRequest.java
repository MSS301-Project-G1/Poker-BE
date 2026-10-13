package com.msspoker.competitionservice.dto.request;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.enums.RoomVisibility;

import jakarta.validation.constraints.*;

/** password is optional; when set, everyone except the owner must supply it to join. */
public record CreateRoomRequest(@NotNull RoomVisibility visibility,
                                @Size(min = CompetitionConstants.MIN_PASSWORD_LENGTH,
                                        max = CompetitionConstants.MAX_PASSWORD_LENGTH) String password,
                                @Min(2) @Max(CompetitionConstants.MAX_TABLE_SEATS) int maxPlayers,
                                @Positive long smallBlind, @Positive long bigBlind) {
    @AssertTrue(message = "Blind lớn phải lớn hơn blind nhỏ.")
    public boolean isBlindsValid() {
        return bigBlind > smallBlind;
    }
}
