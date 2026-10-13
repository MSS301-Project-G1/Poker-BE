package com.msspoker.rankingservice.dto.request;

import java.util.UUID;

/** Body of wallet-service POST /internal/wallet/credit (SPEC §6.1). */
public record WalletCreditRequest(UUID accountId, long amount, String reason, String refId, String idempotencyKey) {
}
