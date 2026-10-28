package com.msspoker.gameservice.client;

import com.msspoker.gameservice.constant.GamePaths;
import com.msspoker.gameservice.dto.response.OwnedSoundResponse;
import com.msspoker.gameservice.exception.GameExceptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/** Asks shop-service for the file of a taunt sound; shop answers only when the player owns that SOUND product. */
@Component
public class ShopSoundClient {
    private final RestClient shop;

    public ShopSoundClient(@Value("${game.shop-url:http://localhost:8088}") String url,
            @Value("${game.shop-timeout-millis:2000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        shop = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public String soundUrl(UUID accountId, UUID productId) {
        OwnedSoundResponse response;
        try {
            response = shop.get().uri(GamePaths.OWNED_SOUND, accountId, productId).retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, reply) -> {
                        throw GameExceptions.tauntSoundNotOwned();
                    })
                    .body(OwnedSoundResponse.class);
        } catch (RestClientException ex) {
            throw GameExceptions.tauntSoundUnavailable();
        }
        if (response == null || response.data() == null || response.data().soundUrl() == null) {
            throw GameExceptions.tauntSoundUnavailable();
        }
        return response.data().soundUrl();
    }
}
