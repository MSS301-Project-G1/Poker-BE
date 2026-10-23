package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.exception.InsufficientBalanceException;
import com.msspoker.matchmakingservice.exception.WalletCallUnknownException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HttpWalletClientContractTest {
    private HttpServer server;
    private HttpWalletClient client;
    private int status = 200;
    private String response = "{}";
    private String body;
    private String path;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/wallet/", exchange -> {
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            path = exchange.getRequestURI().getPath();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new HttpWalletClient("http://127.0.0.1:" + server.getAddress().getPort(), new MatchmakingProperties());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void sendsWalletReasonsAndStableIdempotencyKey() {
        var account = UUID.randomUUID();
        client.debit(new WalletTransferRequest(account, 100, "RANK_ENTRY_FEE", "proposal", "fee-key"));
        assertEquals("/internal/wallet/debit", path);
        assertTrue(body.contains("RANK_ENTRY_FEE"));
        assertTrue(body.contains("fee-key"));
        client.refund(new WalletTransferRequest(account, 100, "RANK_ENTRY_REFUND", "proposal", "refund-key"));
        assertEquals("/internal/wallet/credit", path);
        assertTrue(body.contains("RANK_ENTRY_REFUND"));
    }

    @Test void recognizesActualWallet400InsufficientBalance() {
        status = 400;
        response = "{\"code\":\"WALLET_INSUFFICIENT_BALANCE\"}";
        assertThrows(InsufficientBalanceException.class, () -> client.debit(request()));
    }

    @Test void doesNotPunishPlayerForConcurrentUpdateOrMalformedRequest() {
        status = 409;
        response = "{\"code\":\"WALLET_CONCURRENT_UPDATE\"}";
        assertThrows(WalletCallUnknownException.class, () -> client.debit(request()));
        status = 400;
        response = "{\"code\":\"WALLET_BAD_REQUEST\"}";
        assertThrows(WalletCallUnknownException.class, () -> client.debit(request()));
    }

    private WalletTransferRequest request() {
        return new WalletTransferRequest(UUID.randomUUID(), 100, "RANK_ENTRY_FEE", "proposal", "fee-key");
    }
}
