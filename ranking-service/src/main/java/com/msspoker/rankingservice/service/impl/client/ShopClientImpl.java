package com.msspoker.rankingservice.service.impl.client;

import com.msspoker.rankingservice.dto.request.ShopGrantRequest;
import com.msspoker.rankingservice.service.client.ShopClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** With ranking.shop.mock (dev and test, SPEC §9.4) grants only log, because shop-service is not running. */
@Slf4j
@Component
public class ShopClientImpl implements ShopClient {
    private final RestClient shop;
    private final boolean mock;

    public ShopClientImpl(@LoadBalanced RestClient.Builder restClients,
            @Value("${ranking.shop-url:http://shop-service}") String url,
            @Value("${ranking.shop-timeout-millis:3000}") long timeoutMillis,
            @Value("${ranking.shop.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        shop = restClients.clone().baseUrl(url).requestFactory(factory).build();
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
