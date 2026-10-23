package com.msspoker.matchmakingservice.dto.request;

import java.util.UUID;

public record WalletTransferRequest(
        UUID accountId,
        long amount,
        String reason,
        String refId,
        String idempotencyKey
) {
}
