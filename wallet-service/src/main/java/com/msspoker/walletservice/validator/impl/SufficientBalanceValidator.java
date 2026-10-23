package com.msspoker.walletservice.validator.impl;

import com.msspoker.walletservice.entity.Wallet;
import com.msspoker.walletservice.exception.WalletExceptions;
import com.msspoker.walletservice.validator.DebitValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra số dư đủ trước khi trừ xu (Rule #15 — Tier 2).
 * Order(1) = chạy đầu tiên trong chuỗi DebitValidator.
 */
@Slf4j
@Component
@Order(1)
public class SufficientBalanceValidator implements DebitValidator {

    @Override
    public void validate(Wallet wallet, long amount) {
        if (wallet.getBalance() < amount) {
            log.warn("Insufficient balance: accountId={}, balance={}, required={}",
                    wallet.getAccountId(), wallet.getBalance(), amount);
            throw WalletExceptions.insufficientBalance();
        }
    }
}
