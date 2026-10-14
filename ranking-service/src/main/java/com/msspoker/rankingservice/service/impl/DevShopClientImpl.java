package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.dto.request.ShopGrantRequest;
import com.msspoker.rankingservice.service.ShopClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** SPEC §9.4 mock while shop-service is unavailable locally: always succeeds and logs. */
@Slf4j
@Component
@Profile("dev | test")
public class DevShopClientImpl implements ShopClient {
    @Override
    public void grant(ShopGrantRequest request) {
        log.info("Dev shop grant {} to {} ({})", request.productId(), request.accountId(), request.idempotencyKey());
    }
}
