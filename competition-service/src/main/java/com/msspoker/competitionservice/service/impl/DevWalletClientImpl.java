package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.service.WalletClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** SPEC §9.4 mock while wallet-service is unavailable locally: always succeeds and logs. */
@Slf4j
@Component
@Profile("dev | test")
public class DevWalletClientImpl implements WalletClient {
    @Override
    public void debit(WalletTransactionRequest request) {
        log.info("Dev wallet debit {} coins from {} ({})", request.amount(), request.accountId(), request.idempotencyKey());
    }

    @Override
    public void credit(WalletTransactionRequest request) {
        log.info("Dev wallet credit {} coins to {} ({})", request.amount(), request.accountId(), request.idempotencyKey());
    }
}
