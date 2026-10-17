package com.msspoker.competitionservice.service.impl.client;

import com.msspoker.competitionservice.dto.request.ShopGrantRequest;
import com.msspoker.competitionservice.service.client.ShopClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** With competition.shop.mock (dev and test, SPEC §9.4) item grants only log, because shop-service is not running. */
@Slf4j
@Component
public class ShopClientImpl implements ShopClient {
    private final RestClient shop;
    private final boolean mock;

    public ShopClientImpl(@Value("${competition.shop-url:http://localhost:8088}") String url,
            @Value("${competition.shop-timeout-millis:3000}") long timeoutMillis,
            @Value("${competition.shop.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        shop = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
    }

    @Override
    public void grant(ShopGrantRequest request) {
        if (mock) {
            log.info("Mock shop grant {} to {} ({})", request.productId(), request.accountId(), request.idempotencyKey());
            return;
        }
        shop.post().uri("/internal/inventory/grant").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().toBodilessEntity();
    }
}
