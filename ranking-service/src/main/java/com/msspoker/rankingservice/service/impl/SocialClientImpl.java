package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.service.SocialClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!dev & !test")
public class SocialClientImpl implements SocialClient {
    private final RestClient social;

    public SocialClientImpl(@Value("${ranking.social-url:http://localhost:8084}") String url,
            @Value("${ranking.social-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        social = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    /** SPEC §6.1: GET /internal/friends/{accountId} returns a raw JSON array of account ids. */
    @Override
    public List<UUID> friends(UUID accountId) {
        try {
            UUID[] friends = social.get().uri("/internal/friends/{accountId}", accountId).retrieve().body(UUID[].class);
            if (friends == null) throw RankingExceptions.socialUnavailable();
            return Arrays.asList(friends);
        } catch (RestClientException ex) {
            throw RankingExceptions.socialUnavailable();
        }
    }
}
