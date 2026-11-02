package com.msspoker.common.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Live authority check: JWT signature validation alone cannot revoke a replaced login/tab. */
public class SessionAccessClient {
    private final URI endpoint;
    private final String key;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public SessionAccessClient(String endpoint, String key) {
        this.endpoint = URI.create(endpoint);
        this.key = key;
    }

    public void verify(String token, String tabId) {
        if (token == null || tabId == null) throw new SessionAccessException("AUTH_TAB_REPLACED", 409);
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + token).header("X-Tab-Id", tabId)
                    .header("X-Session-Internal-Key", key).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 204) return;
            String code = response.headers().firstValue("X-Session-Error").orElse("AUTH_SESSION_UNAVAILABLE");
            if (!java.util.Set.of("AUTH_SESSION_REPLACED", "AUTH_TAB_REPLACED", "AUTH_UNAUTHENTICATED",
                    "AUTH_REFRESH_INVALID", "AUTH_ACCOUNT_UNAVAILABLE", "AUTH_EMAIL_VERIFICATION_PENDING").contains(code)) {
                throw new SessionAccessException("AUTH_SESSION_UNAVAILABLE", 503);
            }
            throw new SessionAccessException(code, response.statusCode());
        } catch (SessionAccessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SessionAccessException("AUTH_SESSION_UNAVAILABLE", 503);
        } catch (Exception exception) {
            throw new SessionAccessException("AUTH_SESSION_UNAVAILABLE", 503);
        }
    }
}
