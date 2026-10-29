package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.service.impl.client.SocialClientImpl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub social-service returning the SPEC §6.1 raw object. */
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
    void readsTheFriendshipWithTheInternalKey() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        reply(200, "{\"isFriend\":true,\"isBlocked\":false}");

        FriendshipResponse friendship = client("social-key").friendship(player, other);
        assertTrue(friendship.isFriend());
        assertFalse(friendship.isBlocked());
        assertEquals("/internal/friends/" + player + "/with/" + other, path.get());
        assertEquals("social-key", sentKey.get());

        reply(200, "{\"isFriend\":false,\"isBlocked\":true}");
        assertTrue(client("").friendship(player, other).isBlocked());
        assertNull(sentKey.get());
    }

    @Test
    void refusalsAndOutagesBecomeDependencyErrors() {
        reply(403, "{\"code\":\"SOCIAL_FORBIDDEN\",\"message\":\"Untrusted ingress\"}");
        assertDependencyError(() -> client("wrong-key").friendship(UUID.randomUUID(), UUID.randomUUID()));
        reply(500, "not json");
        assertDependencyError(() -> client("social-key").friendship(UUID.randomUUID(), UUID.randomUUID()));
        server.stop(0);
        assertDependencyError(() -> client("social-key").friendship(UUID.randomUUID(), UUID.randomUUID()));
    }

    private void reply(int status, String body) {
        this.status = status;
        this.response = body;
    }

    private void assertDependencyError(Executable action) {
        assertEquals(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE,
                assertThrows(CompetitionApiException.class, action).getCode());
    }

    private SocialClient client(String internalKey) {
        return new SocialClientImpl(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                internalKey, 2000, false);
    }
}
