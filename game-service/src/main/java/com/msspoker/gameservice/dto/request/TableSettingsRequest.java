package com.msspoker.gameservice.dto.request;

import com.msspoker.gameservice.model.game.TableRules;

import jakarta.validation.constraints.*;

public record TableSettingsRequest(@Positive long smallBlind, @Positive long bigBlind,
                       @Min(1) @Max(TableRules.MAX_CHIPS) long startingChips,
                       @Min(1) @Max(300) int turnTimeSeconds) {
    @AssertTrue(message = "Blind lớn phải lớn hơn blind nhỏ và không vượt chip khởi điểm.")
    public boolean isBlindsValid() {
        return bigBlind > smallBlind && startingChips >= bigBlind;
    }
}
