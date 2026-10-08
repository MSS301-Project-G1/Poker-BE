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

/**
 * MapStruct mapper cho wallet-service (Rule #8).
 * Tự động sinh implementation tại compile-time,
 * loại bỏ manual getter/setter mapping.
 */
@Mapper(componentModel = "spring")
public interface WalletMapper {

    TransactionResponse toTransactionResponse(WalletTransaction tx);

    WalletResponse toWalletResponse(Wallet wallet);

    @Mapping(target = "balance", source = "balance")
    @Mapping(target = "accountId", source = "accountId")
    BalanceResponse toBalanceResponse(Wallet wallet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "balanceAfter", source = "balanceAfter")
    WalletTransaction toCreditTransaction(CreditRequest request, long balanceAfter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "balanceAfter", source = "balanceAfter")
    @Mapping(target = "amount", expression = "java(-request.amount())")
    WalletTransaction toDebitTransaction(DebitRequest request, long balanceAfter);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "balance", source = "balance")
    Wallet toWallet(UUID accountId, long balance);
}
