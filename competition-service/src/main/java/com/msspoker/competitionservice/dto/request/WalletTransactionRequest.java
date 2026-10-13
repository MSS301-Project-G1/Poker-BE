package com.msspoker.competitionservice.dto.request;

import java.util.UUID;

/** Body of wallet-service POST /internal/wallet/debit and /credit (SPEC §6.1). */
public record WalletTransactionRequest(UUID accountId, long amount, String reason, String refId, String idempotencyKey) {
}
