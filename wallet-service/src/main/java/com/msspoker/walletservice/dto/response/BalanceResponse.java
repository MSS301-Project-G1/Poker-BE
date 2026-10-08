package com.msspoker.walletservice.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record BalanceResponse(
        UUID accountId,
        long balance
) {}
