package com.msspoker.walletservice.mapper;

import com.msspoker.walletservice.dto.request.CreditRequest;
import com.msspoker.walletservice.dto.request.DebitRequest;
import com.msspoker.walletservice.dto.response.BalanceResponse;
import com.msspoker.walletservice.dto.response.TransactionResponse;
import com.msspoker.walletservice.dto.response.WalletResponse;
import com.msspoker.walletservice.entity.Wallet;
import com.msspoker.walletservice.entity.WalletTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    TransactionResponse toTransactionResponse(WalletTransaction tx);

    @Mapping(target = "balance", source = "coinBalance")
    @Mapping(target = "coinBalance", source = "coinBalance")
    @Mapping(target = "vipCoinBalance", source = "vipCoinBalance")
    WalletResponse toWalletResponse(Wallet wallet);

    @Mapping(target = "balance", source = "coinBalance")
    @Mapping(target = "coinBalance", source = "coinBalance")
    @Mapping(target = "vipCoinBalance", source = "vipCoinBalance")
    @Mapping(target = "accountId", source = "accountId")
    BalanceResponse toBalanceResponse(Wallet wallet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "balanceAfter", source = "balanceAfter")
    @Mapping(target = "currencyType", source = "request.currencyType")
    WalletTransaction toCreditTransaction(CreditRequest request, long balanceAfter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "balanceAfter", source = "balanceAfter")
    @Mapping(target = "currencyType", source = "request.currencyType")
    @Mapping(target = "amount", expression = "java(-request.amount())")
    WalletTransaction toDebitTransaction(DebitRequest request, long balanceAfter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "coinBalance", source = "balance")
    @Mapping(target = "vipCoinBalance", constant = "0L")
    Wallet toWallet(UUID accountId, long balance);
}
