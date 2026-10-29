package com.msspoker.rankingservice.service.client;

import com.msspoker.rankingservice.enums.RankingError;
import com.msspoker.rankingservice.exception.RankingApiException;
import com.msspoker.rankingservice.service.impl.client.SocialClientImpl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub social-service returning the SPEC §6.1 raw array. */
class SocialClientImplTest {
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> sentKey = new AtomicReference<>();
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/friends/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            sentKey.set(exchange.getRequestHeaders().getFirst("X-Social-Internal-Key"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void readsTheFriendIdsWithTheInternalKey() {
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        reply(200, "[\"" + first + "\",\"" + second + "\"]");
        assertEquals(List.of(first, second), client().friends(player));
        assertEquals("/internal/friends/" + player, path.get());
        assertEquals("social-key", sentKey.get());
        reply(200, "[]");
        assertTrue(client("").friends(player).isEmpty());
        assertNull(sentKey.get());
    }

    @Test
    void errorsAndOutagesBecomeDependencyErrors() {
        reply(403, "{\"code\":\"SOCIAL_FORBIDDEN\",\"message\":\"Untrusted ingress\"}");
        assertDependencyError(() -> client().friends(UUID.randomUUID()));
        reply(500, "{\"code\":\"SOCIAL_INTERNAL_ERROR\"}");
        assertDependencyError(() -> client().friends(UUID.randomUUID()));
        reply(200, "{\"code\":\"SOCIAL_SUCCESS\",\"data\":[]}");
        assertDependencyError(() -> client().friends(UUID.randomUUID()));
        server.stop(0);
        assertDependencyError(() -> client().friends(UUID.randomUUID()));
    }

    private void reply(int status, String body) {
        this.status = status;
        this.response = body;
    }

    private void assertDependencyError(Executable action) {
        assertEquals(RankingError.RANK_DEPENDENCY_UNAVAILABLE, assertThrows(RankingApiException.class, action).getCode());
    }

    private SocialClient client() {
        return client("social-key");
    }

    private SocialClient client(String internalKey) {
        return new SocialClientImpl(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), internalKey,
                2000, false, null, null);
    }
}
