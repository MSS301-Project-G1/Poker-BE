package com.msspoker.gameservice.dto.request;

import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.model.game.TableRules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateTableRequest(@NotNull GameMode mode, @NotNull UUID sourceId,
                          @NotNull @Size(min = 2, max = TableRules.MAX_SEATS) List<@NotNull UUID> playerIds,
                          @Valid TableSettingsRequest settings, @PositiveOrZero long entryFee) {
    @AssertTrue(message = "Danh sách người chơi không được trùng tài khoản.")
    public boolean isPlayersUnique() {
        return playerIds == null || new java.util.HashSet<>(playerIds).size() == playerIds.size();
    }

    @AssertTrue(message = "Trận Normal và Custom không có phí vào.")
    public boolean isEntryFeeValid() {
        return (mode != GameMode.NORMAL && mode != GameMode.CUSTOM) || entryFee == 0;
    }
}
