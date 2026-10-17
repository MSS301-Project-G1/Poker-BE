package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.service.WalletClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
@Profile("!dev & !test")
public class WalletClientImpl implements WalletClient {
    private final RestClient wallet;

    public WalletClientImpl(@Value("${ranking.wallet-url:http://localhost:8089}") String url,
            @Value("${ranking.wallet-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        wallet = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public void credit(WalletCreditRequest request) {
        wallet.post().uri("/internal/wallet/credit").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().toBodilessEntity();
    }
}
