package com.msspoker.gameservice.client;

import com.msspoker.gameservice.constant.GamePaths;
import com.msspoker.gameservice.dto.response.OwnedSoundResponse;
import com.msspoker.gameservice.exception.GameExceptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;

/** Asks shop-service for the file of a taunt sound; shop answers only when the player owns that SOUND product. */
@Component
public class ShopSoundClient {
    private final RestClient shop;
    @Value("${shop.internal-key:}")
    private String shopInternalKey = "";

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
            response = shop.get().uri(GamePaths.OWNED_SOUND, accountId, productId).header("X-Shop-Internal-Key", shopInternalKey).retrieve()
                    .body(OwnedSoundResponse.class);
        } catch (RestClientResponseException ex) {
            String code = errorCode(ex);
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND && "SHOP_ITEM_NOT_OWNED".equals(code)) {
                throw GameExceptions.tauntSoundNotOwned();
            }
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST && "SHOP_SOUND_NOT_PLAYABLE".equals(code)) {
                throw GameExceptions.tauntSoundNotPlayable();
            }
            throw GameExceptions.tauntSoundUnavailable();
        } catch (RestClientException ex) {
            throw GameExceptions.tauntSoundUnavailable();
        }
        if (response == null || response.data() == null || response.data().soundUrl() == null || response.data().soundUrl().isBlank()) {
            throw GameExceptions.tauntSoundUnavailable();
        }
        return response.data().soundUrl();
    }

    private String errorCode(RestClientResponseException exception) {
        try {
            Map<?, ?> error = exception.getResponseBodyAs(Map.class);
            return error != null && error.get("code") instanceof String code ? code : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
