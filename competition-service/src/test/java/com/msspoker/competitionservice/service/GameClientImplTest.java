package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.CreateTableRequest;
import com.msspoker.competitionservice.dto.request.TableSettingsRequest;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.enums.GameMode;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.service.impl.GameClientImpl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub game-service. */
class GameClientImplTest {
    final CreateTableRequest request = new CreateTableRequest(GameMode.CUSTOM, UUID.randomUUID(),
            List.of(UUID.randomUUID(), UUID.randomUUID()), new TableSettingsRequest(10, 20, 1000, 20), 0);
    final AtomicReference<String> sentKey = new AtomicReference<>();
    final AtomicReference<String> sentBody = new AtomicReference<>();
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/tables", exchange -> {
            sentKey.set(exchange.getRequestHeaders().getFirst("X-Game-Service-Key"));
            sentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
    void sendsContractBodyAndServiceKey() {
        UUID tableId = UUID.randomUUID();
        reply(200, "{\"tableId\":\"" + tableId + "\",\"matchId\":\"" + UUID.randomUUID() + "\"}");

        assertEquals(tableId, client("game-key").createTable(request).tableId());
        assertEquals("game-key", sentKey.get());
        assertTrue(sentBody.get().contains("\"mode\":\"CUSTOM\""));
        assertTrue(sentBody.get().contains("\"sourceId\":\"" + request.sourceId() + "\""));
        assertTrue(sentBody.get().contains("\"startingChips\":1000"));

        client("").createTable(request);
        assertNull(sentKey.get());
    }

    @Test
    void mapsGameErrorsToCompetitionErrors() {
        reply(409, "{\"code\":\"GAME_SOURCE_CONFLICT\",\"message\":\"x\"}");
        assertError(CompetitionError.COMP_SOURCE_CONFLICT);
        reply(409, "{\"code\":\"GAME_PLAYER_BUSY\",\"message\":\"x\"}");
        assertError(CompetitionError.COMP_PLAYER_BUSY);
        reply(403, "{\"code\":\"GAME_FORBIDDEN\",\"message\":\"x\"}");
        assertError(CompetitionError.COMP_FORBIDDEN);
        reply(401, "{\"code\":\"GAME_UNAUTHORIZED\",\"message\":\"x\"}");
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE);
        reply(500, "not json");
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE);

        server.stop(0);
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE);
    }

    private void reply(int status, String body) {
        this.status = status;
        this.response = body;
    }

    private void assertError(CompetitionError code) {
        var ex = assertThrows(CompetitionApiException.class, () -> client("").createTable(request));
        assertEquals(code, ex.getCode());
    }

    private GameClient client(String key) {
        return new GameClientImpl("http://127.0.0.1:" + server.getAddress().getPort(), key, 2000);
    }
}
