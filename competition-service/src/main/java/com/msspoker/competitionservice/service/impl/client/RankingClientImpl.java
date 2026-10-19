package com.msspoker.competitionservice.service.impl.client;

import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.client.RankingClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/** ranking-service belongs to the same owner, so dev calls the real service like game-service. */
@Component
public class RankingClientImpl implements RankingClient {
    private static final ParameterizedTypeReference<List<RankedPlayerResponse>> RANKED_PLAYERS =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient ranking;

    public RankingClientImpl(@Value("${competition.ranking-url:http://localhost:8086}") String url,
            @Value("${competition.ranking-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        ranking = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public List<RankedPlayerResponse> top(int limit) {
        try {
            List<RankedPlayerResponse> top = ranking.get().uri("/internal/ranking/top?limit={limit}", limit)
                    .retrieve().body(RANKED_PLAYERS);
            if (top == null) throw CompetitionExceptions.rankingUnavailable();
            return top;
        } catch (RestClientException ex) {
            throw CompetitionExceptions.rankingUnavailable();
        }
    }
}
