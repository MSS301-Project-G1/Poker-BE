package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.exception.GameApiException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub auth-service restrictions endpoint. */
class AccountRestrictionsValidatorTest {
    private static final String CLEAN = "{\"banned\":false,\"chatBannedUntil\":null,\"rankBannedUntil\":null}";
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/accounts/", exchange -> {
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
    void cleanAccountsMayOpenEveryMode() {
        reply(200, CLEAN);
        for (GameMode mode : GameMode.values()) {
            assertDoesNotThrow(() -> validator().validate(request(mode)));
        }
    }

    @Test
    void bannedAccountCannotOpenAnyTable() {
        reply(200, "{\"banned\":true,\"chatBannedUntil\":null,\"rankBannedUntil\":null}");
        assertError(GameError.GAME_FORBIDDEN, GameMode.NORMAL);
    }

    @Test
    void activeRankBanBlocksOnlyRankTables() {
        reply(200, "{\"banned\":false,\"chatBannedUntil\":null,\"rankBannedUntil\":\""
                + Instant.now().plusSeconds(3600) + "\"}");
        assertError(GameError.GAME_FORBIDDEN, GameMode.RANK);
        assertDoesNotThrow(() -> validator().validate(request(GameMode.NORMAL)));

        reply(200, "{\"banned\":false,\"chatBannedUntil\":null,\"rankBannedUntil\":\""
                + Instant.now().minusSeconds(60) + "\"}");
        assertDoesNotThrow(() -> validator().validate(request(GameMode.RANK)));
    }

    @Test
    void missingEndpointOrUnreachableAuthFailsClosed() {
        reply(404, "{}");
        assertError(GameError.GAME_DEPENDENCY_UNAVAILABLE, GameMode.NORMAL);
        reply(500, "not json");
        assertError(GameError.GAME_DEPENDENCY_UNAVAILABLE, GameMode.NORMAL);

        int port = server.getAddress().getPort();
        server.stop(0);
        var offline = new AccountRestrictionsValidator("http://127.0.0.1:" + port, 500);
        var ex = assertThrows(GameApiException.class, () -> offline.validate(request(GameMode.NORMAL)));
        assertEquals(GameError.GAME_DEPENDENCY_UNAVAILABLE, ex.getCode());
    }

    private void assertError(GameError code, GameMode mode) {
        var ex = assertThrows(GameApiException.class, () -> validator().validate(request(mode)));
        assertEquals(code, ex.getCode());
    }

    private void reply(int status, String response) {
        this.status = status;
        this.response = response;
    }

    private AccountRestrictionsValidator validator() {
        return new AccountRestrictionsValidator("http://127.0.0.1:" + server.getAddress().getPort(), 2000);
    }

    private static CreateTableRequest request(GameMode mode) {
        long fee = mode == GameMode.NORMAL || mode == GameMode.CUSTOM ? 0 : 100;
        return new CreateTableRequest(mode, UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), null, fee);
    }
}
