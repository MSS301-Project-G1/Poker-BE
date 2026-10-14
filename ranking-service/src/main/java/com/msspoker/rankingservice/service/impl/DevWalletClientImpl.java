package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.service.WalletClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** SPEC §9.4 mock while wallet-service is unavailable locally: always succeeds and logs. */
@Slf4j
@Component
@Profile("dev | test")
public class DevWalletClientImpl implements WalletClient {
    @Override
    public void credit(WalletCreditRequest request) {
        log.info("Dev wallet credit {} coins to {} ({})", request.amount(), request.accountId(), request.idempotencyKey());
    }
}
