package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.request.ShopGrantRequest;
import com.msspoker.competitionservice.service.impl.client.ShopClientImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class ShopInternalKeyTest {
    @Test
    void rewardGrantSendsInternalKeyAndPreservesIdempotency() throws Exception {
        var key = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/inventory/grant", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Shop-Internal-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new ShopClientImpl(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(), 2000, false);
            ReflectionTestUtils.setField(client, "shopInternalKey", "shop-test-private-key");
            client.grant(new ShopGrantRequest(UUID.randomUUID(), UUID.randomUUID(), "reward:stable-key"));
            assertThat(key.get()).isEqualTo("shop-test-private-key");
            assertThat(body.get()).contains("reward:stable-key");
        } finally { server.stop(0); }
    }
}
