package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;

public interface WalletClient {
    void debit(WalletTransferRequest request);
    void refund(WalletTransferRequest request);
}
