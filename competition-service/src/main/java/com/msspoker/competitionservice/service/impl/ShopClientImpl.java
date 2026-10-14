package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.dto.request.ShopGrantRequest;
import com.msspoker.competitionservice.service.ShopClient;

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
public class ShopClientImpl implements ShopClient {
    private final RestClient shop;

    public ShopClientImpl(@Value("${competition.shop-url:http://localhost:8088}") String url,
            @Value("${competition.shop-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        shop = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public void grant(ShopGrantRequest request) {
        shop.post().uri("/internal/inventory/grant").contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().toBodilessEntity();
    }
}
