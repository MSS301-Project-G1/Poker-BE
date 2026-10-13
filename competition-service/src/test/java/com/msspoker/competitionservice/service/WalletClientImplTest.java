package com.msspoker.competitionservice.service;

import com.msspoker.competitionservice.dto.request.WalletTransactionRequest;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.exception.CompetitionApiException;
import com.msspoker.competitionservice.service.impl.WalletClientImpl;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP client against a stub wallet-service using its error body format. */
class WalletClientImplTest {
    final WalletTransactionRequest request = new WalletTransactionRequest(UUID.randomUUID(), 100,
            "TOURNAMENT_ENTRY_FEE", UUID.randomUUID().toString(), "tournament-fee:t:a");
    final AtomicReference<String> path = new AtomicReference<>();
    final AtomicReference<String> body = new AtomicReference<>();
    HttpServer server;
    volatile int status;
    volatile String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/wallet/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
    void sendsContractBodyToDebitAndCredit() {
        reply(200, "{\"code\":\"WALLET_SUCCESS\",\"data\":{}}");
        client().debit(request);
        assertEquals("/internal/wallet/debit", path.get());
        assertTrue(body.get().contains("\"reason\":\"TOURNAMENT_ENTRY_FEE\""));
        assertTrue(body.get().contains("\"idempotencyKey\":\"tournament-fee:t:a\""));
        client().credit(request);
        assertEquals("/internal/wallet/credit", path.get());
    }

    @Test
    void mapsInsufficientBalanceAndOutages() {
        reply(400, "{\"code\":\"WALLET_INSUFFICIENT_BALANCE\",\"message\":\"x\",\"timestamp\":\"2026-10-13T08:00:00Z\"}");
        assertError(CompetitionError.COMP_INSUFFICIENT_BALANCE, () -> client().debit(request));
        reply(400, "{\"code\":\"WALLET_VALIDATION_ERROR\",\"message\":\"x\"}");
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, () -> client().debit(request));
        reply(500, "oops");
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, () -> client().credit(request));
        server.stop(0);
        assertError(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, () -> client().credit(request));
    }

    private void reply(int status, String body) {
        this.status = status;
        this.response = body;
    }

    private void assertError(CompetitionError code, Executable action) {
        var ex = assertThrows(CompetitionApiException.class, action);
        assertEquals(code, ex.getCode());
    }

    private WalletClient client() {
        return new WalletClientImpl("http://127.0.0.1:" + server.getAddress().getPort(), 2000);
    }
}
