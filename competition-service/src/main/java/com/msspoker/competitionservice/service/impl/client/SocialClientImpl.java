package com.msspoker.competitionservice.service.impl.client;

import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.exception.CompetitionExceptions;
import com.msspoker.competitionservice.service.client.SocialClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/** With competition.social.mock (dev and test, SPEC §9.4) social-service is not running, so everyone is a friend. */
@Component
public class SocialClientImpl implements SocialClient {
    private final RestClient social;
    private final boolean mock;

    public SocialClientImpl(@LoadBalanced RestClient.Builder restClients,
            @Value("${competition.social-url:http://social-service}") String url,
            @Value("${competition.social-timeout-millis:3000}") long timeoutMillis,
            @Value("${competition.social.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        social = restClients.clone().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
    }

    @Override
    public FriendshipResponse friendship(UUID accountId, UUID otherId) {
        if (mock) return new FriendshipResponse(true, false);
        try {
            FriendshipResponse response = social.get().uri("/internal/friends/{a}/with/{b}", accountId, otherId)
                    .retrieve().body(FriendshipResponse.class);
            if (response == null) throw CompetitionExceptions.socialUnavailable();
            return response;
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException: no registered instance of the service.
            throw CompetitionExceptions.socialUnavailable();
        }
    }
}
