package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.service.impl.RankingClientImpl;

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

/** Exercises the real HTTP client against a stub ranking-service using its response format. */
class RankingClientImplTest {
    final AtomicReference<String> query = new AtomicReference<>();
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/ranking/top", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
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
    void readsTheTopListInLeaderboardOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        status = 200;
        response = """
                [{"position":1,"accountId":"%s","elo":1720,"tier":"CARD_GOD","winStreak":3,"matchesPlayed":40},
                 {"position":2,"accountId":"%s","elo":1650,"tier":"CARD_GOD","winStreak":0,"matchesPlayed":38}]
                """.formatted(first, second);
        List<RankedPlayerResponse> top = client().top(32);
        assertEquals("limit=32", query.get());
        assertEquals(List.of(first, second), top.stream().map(RankedPlayerResponse::accountId).toList());
        assertEquals(1720, top.getFirst().elo());
    }

    @Test
    void outagesBecomeDependencyErrors() {
        status = 500;
        response = "{\"code\":\"RANK_INTERNAL_ERROR\",\"message\":\"x\"}";
        assertEquals(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE,
                assertThrows(CompetitionApiException.class, () -> client().top(32)).getCode());
        server.stop(0);
        assertEquals(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE,
                assertThrows(CompetitionApiException.class, () -> client().top(32)).getCode());
    }

    private RankingClient client() {
        return new RankingClientImpl("http://127.0.0.1:" + server.getAddress().getPort(), 2000);
    }
}
