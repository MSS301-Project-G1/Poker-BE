package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.AccountRestrictionsResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.service.RestrictionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "matchmaking.restriction.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpRestrictionClient implements RestrictionClient {
    private final RestClient identity;
    private final String serviceKey;

    public HttpRestrictionClient(
            @Value("${matchmaking.identity-url:http://localhost:8090}") String identityUrl,
            @Value("${matchmaking.game.service-key:}") String serviceKey,
            MatchmakingProperties properties
    ) {
        Duration timeout = properties.getRestriction().getTimeout();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        identity = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public AccountRestrictionsResponse restrictions(UUID accountId) {
        try {
            AccountRestrictionsResponse response = identity.get()
                    .uri("/internal/accounts/{id}/restrictions", accountId)
                    .header("X-Game-Service-Key", serviceKey)
                    .retrieve()
                    .body(AccountRestrictionsResponse.class);
            if (response == null) throw new IllegalStateException("Identity trả response rỗng");
            return response;
        } catch (RuntimeException exception) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_DEPENDENCY_UNAVAILABLE,
                    HttpStatus.BAD_GATEWAY,
                    "Không kiểm tra được hạn chế tài khoản từ identity-service."
            );
        }
    }
}
