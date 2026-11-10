package com.msspoker.matchmakingservice.service.client.stub;

import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.service.client.WalletClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "matchmaking.wallet.stub-enabled", havingValue = "true")
public class StubWalletClient implements WalletClient {
    @Override public void debit(WalletTransferRequest request) { }
    @Override public void refund(WalletTransferRequest request) { }
}
