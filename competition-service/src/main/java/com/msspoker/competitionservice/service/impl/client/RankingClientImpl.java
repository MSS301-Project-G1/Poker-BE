package com.msspoker.competitionservice.service.impl.client;

import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.client.RankingClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
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

    public RankingClientImpl(@LoadBalanced RestClient.Builder restClients,
            @Value("${competition.ranking-url:http://ranking-service}") String url,
            @Value("${competition.ranking-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        ranking = restClients.clone().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public List<RankedPlayerResponse> top(int limit) {
        try {
            List<RankedPlayerResponse> top = ranking.get().uri("/internal/ranking/top?limit={limit}", limit)
                    .retrieve().body(RANKED_PLAYERS);
            if (top == null) throw CompetitionExceptions.rankingUnavailable();
            return top;
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException: no registered instance of the service.
            throw CompetitionExceptions.rankingUnavailable();
        }
    }
}
