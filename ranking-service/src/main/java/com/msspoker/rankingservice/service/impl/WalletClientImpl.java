package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.service.WalletClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** With ranking.wallet.mock (dev and test, SPEC §9.4) credits only log, because wallet-service is not running. */
@Slf4j
@Component
public class WalletClientImpl implements WalletClient {
    private final RestClient wallet;
    private final boolean mock;

    public WalletClientImpl(@Value("${ranking.wallet-url:http://localhost:8089}") String url,
            @Value("${ranking.wallet-timeout-millis:3000}") long timeoutMillis,
            @Value("${ranking.wallet.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        wallet = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
    }

    @Override
    public void credit(WalletCreditRequest request) {
        if (mock) {
            log.info("Mock wallet credit {} coins to {} ({})", request.amount(), request.accountId(), request.idempotencyKey());
            return;
        }
        wallet.post().uri("/internal/wallet/credit").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().toBodilessEntity();
    }
}
