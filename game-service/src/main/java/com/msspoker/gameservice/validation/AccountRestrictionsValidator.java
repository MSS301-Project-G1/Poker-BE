package com.msspoker.gameservice.validation;

import com.msspoker.gameservice.api.GameDtos.CreateTable;
import com.msspoker.gameservice.engine.GameMode;
import com.msspoker.gameservice.exception.GameExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

import java.time.Instant;

@Component
@Profile("!dev & !test")
public class AccountRestrictionsValidator implements CreateTableValidator {
    private final RestClient identity;

    public AccountRestrictionsValidator(@Value("${game.identity-url:http://localhost:8081}") String url,
            @Value("${game.identity-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        identity = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public record Restrictions(boolean banned, Instant chatBannedUntil, Instant rankBannedUntil) {
    }

    @Override
    public void validate(CreateTable request) {
        for (var playerId : request.playerIds()) {
            Restrictions restrictions;
            try {
                restrictions = identity.get().uri("/internal/accounts/{id}/restrictions", playerId)
                        .retrieve().body(Restrictions.class);
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
