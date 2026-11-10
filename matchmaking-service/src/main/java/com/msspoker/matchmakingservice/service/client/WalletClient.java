package com.msspoker.matchmakingservice.service.client;

import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;

public interface WalletClient {
    void debit(WalletTransferRequest request);
    void refund(WalletTransferRequest request);
}
