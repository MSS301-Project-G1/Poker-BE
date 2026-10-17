package com.msspoker.walletservice.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record WalletResponse(
        UUID accountId,
        long balance,
        long coinBalance,
        long vipCoinBalance,
        Instant createdAt,
        Instant updatedAt
) {}
