package com.msspoker.shopservice.client;

import com.msspoker.shopservice.config.DataInitProperties;
import com.msspoker.shopservice.dto.response.AuthDemoAccountResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves auth-service demo accounts, whose ids differ per database, for the seed grants.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shop.data-init.enabled", havingValue = "true")
public class AuthClient {

    private static final String DEMO_ACCOUNTS_PATH = "/internal/demo-accounts?email={email}";
    // Runs on the shared scheduler thread, so a hanging auth-service must not hold it for long.
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient auth;

    public AuthClient(DataInitProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        auth = RestClient.builder().baseUrl(properties.authServiceUrl()).requestFactory(factory).build();
    }

    /** Empty while auth-service is unreachable or has not created the demo account yet. */
    public Optional<UUID> findDemoAccountId(String email) {
        try {
            AuthDemoAccountResponse response = auth.get().uri(DEMO_ACCOUNTS_PATH, email)
                    .retrieve().body(AuthDemoAccountResponse.class);
            return Optional.ofNullable(response)
                    .map(AuthDemoAccountResponse::result)
                    .map(AuthDemoAccountResponse.Result::accountId);
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Demo account {} does not exist in auth-service yet", email);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.debug("auth-service unavailable for demo account {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }
}
