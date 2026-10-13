package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.SocialClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
@Profile("!dev & !test")
public class SocialClientImpl implements SocialClient {
    private final RestClient social;

    public SocialClientImpl(@Value("${competition.social-url:http://localhost:8084}") String url,
            @Value("${competition.social-timeout-millis:3000}") long timeoutMillis) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        social = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public FriendshipResponse friendship(UUID accountId, UUID otherId) {
        try {
            FriendshipResponse response = social.get().uri("/internal/friends/{a}/with/{b}", accountId, otherId)
                    .retrieve().body(FriendshipResponse.class);
            if (response == null) throw CompetitionExceptions.socialUnavailable();
            return response;
        } catch (RestClientException ex) {
            throw CompetitionExceptions.socialUnavailable();
        }
    }
}
