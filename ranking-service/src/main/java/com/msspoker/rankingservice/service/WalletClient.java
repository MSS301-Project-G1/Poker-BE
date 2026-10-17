package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;

public interface WalletClient {
    /** Throws when the wallet did not accept the credit; the idempotency key makes retries safe. */
    void credit(WalletCreditRequest request);
}
