package com.msspoker.gameservice.validator;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.enums.GameError;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.service.TableService;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** CreateTable with restrictions enabled, as outside the dev profile. */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:poker-restrictions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CreateTableRestrictionsTest {
    static final AtomicInteger AUTH_CALLS = new AtomicInteger();
    static final HttpServer AUTH = startAuth();

    @Autowired TableService tables;
    @Autowired List<CreateTableValidator> validators;

    @DynamicPropertySource
    static void auth(DynamicPropertyRegistry registry) {
        registry.add("game.restrictions.enabled", () -> "true");
        registry.add("game.identity-url", () -> "http://127.0.0.1:" + AUTH.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        AUTH.stop(0);
    }

    @Test
    void localAvailabilityRunsBeforeTheRemoteRestrictionCheck() {
        assertInstanceOf(PlayerAvailabilityValidator.class, validators.getFirst());
        assertInstanceOf(AccountRestrictionsValidator.class, validators.getLast());

        UUID seated = UUID.randomUUID();
        tables.create(new CreateTableRequest(GameMode.NORMAL, UUID.randomUUID(), List.of(seated, UUID.randomUUID()), null, 0));
        int callsAfterCreate = AUTH_CALLS.get();
        assertEquals(2, callsAfterCreate);

        var ex = assertThrows(GameApiException.class, () -> tables.create(new CreateTableRequest(GameMode.NORMAL,
                UUID.randomUUID(), List.of(seated, UUID.randomUUID()), null, 0)));
        assertEquals(GameError.GAME_PLAYER_BUSY, ex.getCode());
        assertEquals(callsAfterCreate, AUTH_CALLS.get());
    }

    private static HttpServer startAuth() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/accounts/", exchange -> {
                AUTH_CALLS.incrementAndGet();
                byte[] bytes = "{\"banned\":false,\"chatBannedUntil\":null,\"rankBannedUntil\":null}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
