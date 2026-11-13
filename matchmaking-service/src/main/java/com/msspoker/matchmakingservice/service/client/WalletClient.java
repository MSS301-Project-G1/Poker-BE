package com.msspoker.matchmakingservice.service.client;

import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;

import java.util.UUID;

public interface WalletClient {
    long coinBalance(UUID accountId);

    void debit(WalletTransferRequest request);

    void refund(WalletTransferRequest request);
}
