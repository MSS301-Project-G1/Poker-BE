package com.msspoker.walletservice.service;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.PageResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WalletService {

    TransactionResponse credit(CreditRequest request);

    TransactionResponse debit(DebitRequest request);

    BalanceResponse getBalance(UUID accountId);

    WalletResponse getMyWallet(UUID accountId);

    PageResponse<TransactionResponse> getMyTransactions(UUID accountId, Pageable pageable);
}
