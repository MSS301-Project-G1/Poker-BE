package com.msspoker.gameservice.dto.request;

import com.msspoker.gameservice.model.game.TableRules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateMatchSettingsRequest(@Valid @NotNull TableSettingsRequest settings,
                             @Min(2) @Max(TableRules.MAX_SEATS) int minPlayers,
                             @Min(2) @Max(TableRules.MAX_SEATS) int maxPlayers) {
    @AssertTrue(message = "Số người tối thiểu không được lớn hơn tối đa.")
    public boolean isPlayerRangeValid() {
        return minPlayers <= maxPlayers;
    }
}
