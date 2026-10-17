package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;

public interface WalletClient {
    /** Throws COMP_INSUFFICIENT_BALANCE when the wallet refuses; the idempotency key makes retries safe. */
    void debit(WalletTransactionRequest request);

    void credit(WalletTransactionRequest request);
}
