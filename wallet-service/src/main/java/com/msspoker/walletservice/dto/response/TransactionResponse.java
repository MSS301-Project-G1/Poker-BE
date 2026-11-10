package com.msspoker.walletservice.dto.response;

import com.msspoker.walletservice.enums.CurrencyType;
import com.msspoker.walletservice.enums.TransactionReason;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TransactionResponse(
        UUID id,
        UUID accountId,
        long amount,
        CurrencyType currencyType,
        TransactionReason reason,
        String refId,
        String idempotencyKey,
        long balanceAfter,
        Instant createdAt
) {}
