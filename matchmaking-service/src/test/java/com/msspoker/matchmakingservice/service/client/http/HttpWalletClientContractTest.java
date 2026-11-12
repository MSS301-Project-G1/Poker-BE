package com.msspoker.matchmakingservice.service.client.http;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.exception.InsufficientBalanceException;
import com.msspoker.matchmakingservice.exception.WalletCallUnknownException;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import org.springframework.http.HttpStatus;
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
    private String suppliedKey;
    private String method;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/wallet/", exchange -> {
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            path = exchange.getRequestURI().getPath();
            suppliedKey = exchange.getRequestHeaders().getFirst("X-Wallet-Internal-Key");
            method = exchange.getRequestMethod();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new HttpWalletClient("http://127.0.0.1:" + server.getAddress().getPort(), new MatchmakingProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(client, "walletInternalKey", "wallet-test-key");
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void readsCoinBalanceFromTheWalletEnvelopeWithTheInternalKey() {
        var account = UUID.randomUUID();
        response = balance(account, "100");
        assertEquals(100L, client.coinBalance(account));
        assertEquals("GET", method);
        assertEquals("/internal/wallet/" + account + "/balance", path);
        assertEquals("wallet-test-key", suppliedKey);
        assertEquals("", body);
    }

    @Test void vipCoinCannotSubstituteForCoins() {
        var account = UUID.randomUUID();
        response = balance(account, "0");
        assertEquals(0L, client.coinBalance(account));
    }

    @Test void rejectsMissingForeignOrInvalidCoinBalancesInsteadOfAssumingZero() {
        var account = UUID.randomUUID();
        String[] invalid = {
                "{}", "null", "not-json", balance(UUID.randomUUID(), "100"),
                balance(account, "100").replace("WALLET_SUCCESS", "WALLET_ERROR"),
                balance(account, "null"), balance(account, "-1"), balance(account, "\"100\""),
                balance(account, "100.5"), balance(account, "9223372036854775808"),
                "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"accountId\":\"" + account + "\",\"balance\":100,\"vipCoinBalance\":999999}}"
        };
        for (String value : invalid) {
            response = value;
            dependencyFailure(account);
        }
    }

    @Test void upstreamErrorsAreNotReportedAsPlayerInsufficientBalance() {
        var account = UUID.randomUUID();
        response = "{\"code\":\"WALLET_INSUFFICIENT_BALANCE\"}";
        for (int code : new int[]{400, 403, 404, 500, 503, 504}) {
            status = code;
            dependencyFailure(account);
        }
    }

    @Test void connectionFailureDoesNotProduceAPassingBalance() {
        server.stop(0);
        server = null;
        dependencyFailure(UUID.randomUUID());
    }

    private void dependencyFailure(UUID account) {
        var error = assertThrows(MatchmakingApiException.class, () -> client.coinBalance(account));
        assertEquals(MatchmakingError.MM_DEPENDENCY_UNAVAILABLE, error.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
    }

    private String balance(UUID account, String coins) {
        return "{\"code\":\"WALLET_SUCCESS\",\"data\":{\"accountId\":\"" + account
                + "\",\"coinBalance\":" + coins + ",\"vipCoinBalance\":999999}}";
    }

    @Test void sendsWalletReasonsAndStableIdempotencyKey() {
        var account = UUID.randomUUID();
        client.debit(new WalletTransferRequest(account, 100, "RANK_ENTRY_FEE", "proposal", "fee-key"));
        assertEquals("/internal/wallet/debit", path);
        assertEquals("wallet-test-key", suppliedKey);
        assertTrue(body.contains("RANK_ENTRY_FEE"));
        assertTrue(body.contains("fee-key"));
        client.refund(new WalletTransferRequest(account, 100, "RANK_ENTRY_REFUND", "proposal", "refund-key"));
        assertEquals("/internal/wallet/credit", path);
        assertEquals("wallet-test-key", suppliedKey);
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
