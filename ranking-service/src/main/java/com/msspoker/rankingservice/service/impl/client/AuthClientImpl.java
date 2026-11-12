package com.msspoker.rankingservice.service.impl.client;

import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.service.client.AuthClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** With ranking.auth.mock (dev and test, SPEC §9.4) auth-service is not called and nobody is an admin. */
@Component
public class AuthClientImpl implements AuthClient {
    private final RestClient auth;
    private final boolean mock;

    public AuthClientImpl(@LoadBalanced RestClient.Builder restClients,
            @Value("${ranking.auth-url:http://auth-service}") String url,
            @Value("${ranking.auth-timeout-millis:3000}") long timeoutMillis,
            @Value("${ranking.auth.mock:false}") boolean mock) {
        Duration timeout = Duration.ofMillis(timeoutMillis);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        auth = restClients.clone().baseUrl(url).requestFactory(factory).build();
        this.mock = mock;
    }

    /** auth-service GET /internal/accounts/admins returns a raw JSON array of account ids. */
    @Override
    public List<UUID> adminIds() {
        if (mock) return List.of();
        try {
            UUID[] admins = auth.get().uri("/internal/accounts/admins").retrieve().body(UUID[].class);
            if (admins == null) throw RankingExceptions.authUnavailable();
            return Arrays.asList(admins);
        } catch (RestClientException | IllegalStateException ex) {
            // IllegalStateException: no registered instance of the service.
            throw RankingExceptions.authUnavailable();
        }
    }
}
