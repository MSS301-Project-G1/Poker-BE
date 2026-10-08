package com.msspoker.walletservice.dto.request;

import com.msspoker.walletservice.enums.TransactionReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreditRequest(
        @NotNull(message = "accountId is required")
        UUID accountId,

        @NotNull(message = "amount is required")
        @Min(value = 1, message = "amount must be greater than 0")
        Long amount,

        @NotNull(message = "reason is required")
        TransactionReason reason,

        String refId,

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey
) {}
