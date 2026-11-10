package com.msspoker.rankingservice.service.client;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.service.impl.client.WalletClientImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class WalletInternalKeyTest {
    @Test
    void sendsPrivateKeyWithRewardCredit() throws Exception {
        var suppliedKey = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/wallet/credit", exchange -> {
            suppliedKey.set(exchange.getRequestHeaders().getFirst("X-Wallet-Internal-Key"));
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new WalletClientImpl(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), 2000, false);
            ReflectionTestUtils.setField(client, "walletInternalKey", "ranking-wallet-test-key");
            client.credit(new WalletCreditRequest(UUID.randomUUID(), 100, "TUTORIAL_REWARD", null, "tutorial:test"));
            assertThat(suppliedKey.get()).isEqualTo("ranking-wallet-test-key");
        } finally { server.stop(0); }
    }
}
