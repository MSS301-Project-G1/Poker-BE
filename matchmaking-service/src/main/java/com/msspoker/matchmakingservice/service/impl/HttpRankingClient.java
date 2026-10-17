package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.PlayerRankingResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.service.RankingClient;
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
@ConditionalOnProperty(name = "matchmaking.ranking.stub-enabled", havingValue = "false", matchIfMissing = true)
public class HttpRankingClient implements RankingClient {
    private final RestClient ranking;

    public HttpRankingClient(
            @Value("${matchmaking.ranking-url:http://localhost:8086}") String rankingUrl,
            MatchmakingProperties properties
    ) {
        Duration timeout = properties.getRanking().getTimeout();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        factory.setReadTimeout(timeout);
        this.ranking = RestClient.builder().baseUrl(rankingUrl).requestFactory(factory).build();
    }

    @Override
    public PlayerRankingResponse ranking(UUID accountId) {
        try {
            PlayerRankingResponse response = ranking.get()
                    .uri("/internal/ranking/{accountId}", accountId)
                    .retrieve()
                    .body(PlayerRankingResponse.class);
            if (response == null || !accountId.equals(response.accountId())) {
                throw new IllegalStateException("Ranking trả sai account");
            }
            return response;
        } catch (RuntimeException exception) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_DEPENDENCY_UNAVAILABLE,
                    HttpStatus.BAD_GATEWAY,
                    "Không đọc được Elo từ ranking-service."
            );
        }
    }
}
