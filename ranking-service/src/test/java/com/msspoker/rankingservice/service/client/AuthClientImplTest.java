package com.msspoker.rankingservice.service.client;

import com.msspoker.rankingservice.enums.RankingError;
import com.msspoker.rankingservice.exception.RankingApiException;
import com.msspoker.rankingservice.service.impl.client.AuthClientImpl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub auth-service returning the raw id array. */
class AuthClientImplTest {
    final AtomicReference<String> path = new AtomicReference<>();
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/accounts/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
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
    void readsTheAdminIds() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        reply(200, "[\"" + first + "\",\"" + second + "\"]");
        assertEquals(List.of(first, second), client(false).adminIds());
        assertEquals("/internal/accounts/admins", path.get());
        reply(200, "[]");
        assertTrue(client(false).adminIds().isEmpty());
    }

    @Test
    void mockNeverCallsAuth() {
        reply(500, "{}");
        assertTrue(client(true).adminIds().isEmpty());
        assertNull(path.get());
    }

    @Test
    void errorsAndOutagesBecomeDependencyErrors() {
        reply(500, "{\"code\":\"AUTH_INTERNAL_ERROR\"}");
        assertDependencyError(() -> client(false).adminIds());
        reply(200, "{\"result\":[]}");
        assertDependencyError(() -> client(false).adminIds());
        server.stop(0);
        assertDependencyError(() -> client(false).adminIds());
    }

    private void reply(int status, String body) {
        this.status = status;
        this.response = body;
    }

    private void assertDependencyError(Executable action) {
        assertEquals(RankingError.RANK_DEPENDENCY_UNAVAILABLE, assertThrows(RankingApiException.class, action).getCode());
    }

    private AuthClient client(boolean mock) {
        return new AuthClientImpl(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), 2000, mock);
    }
}
