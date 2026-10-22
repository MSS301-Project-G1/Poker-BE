package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.constant.GamePaths;
import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.response.AccountRestrictionsResponse;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.exception.GameExceptions;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

import java.time.Instant;

/** Remote check runs after the local availability check, so a busy roster never calls auth-service. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "game.restrictions.enabled", havingValue = "true", matchIfMissing = true)
public class AccountRestrictionsValidator implements CreateTableValidator {
    private final RestClient identity;
    private final String serviceKey;

    public AccountRestrictionsValidator(@Value("${game.identity-url:http://localhost:8090}") String url,
            @Value("${game.service-key:}") String serviceKey,
            @Value("${game.identity-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        identity = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public void validate(CreateTableRequest request) {
        for (var playerId : request.playerIds()) {
            AccountRestrictionsResponse restrictions;
            try {
                restrictions = identity.get().uri(GamePaths.ACCOUNT_RESTRICTIONS, playerId)
                        .header("X-Game-Service-Key", serviceKey)
                        .retrieve().body(AccountRestrictionsResponse.class);
            } catch (RestClientException ex) {
                throw GameExceptions.dependencyUnavailable();
            }
            if (restrictions == null) throw GameExceptions.dependencyUnavailable();
            if (restrictions.banned() || (request.mode() == GameMode.RANK
                    && restrictions.rankBannedUntil() != null && restrictions.rankBannedUntil().isAfter(Instant.now()))) {
                throw GameExceptions.forbidden();
            }
        }
    }
}
