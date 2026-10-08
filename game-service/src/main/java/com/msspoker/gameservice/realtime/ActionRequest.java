package com.msspoker.gameservice.realtime;

import com.msspoker.gameservice.engine.ActionType;
import jakarta.validation.constraints.*;

public record ActionRequest(@NotNull ActionType type, @Positive Long amount, @PositiveOrZero Long actionSequence) {
    @AssertTrue(message = "Raise cần tổng mức cược; các hành động khác không nhận amount.")
    public boolean isAmountValid() {
        return type == null || (type == ActionType.RAISE ? amount != null : amount == null);
    }
}
