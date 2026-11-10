package com.msspoker.gameservice.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class ShopSoundInternalKeyTest {
    @Test
    void ownedSoundReadSendsShopKeyAndReadsExistingEnvelope() throws Exception {
        var key = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var accountId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        server.createContext("/internal/inventory/" + accountId + "/sounds/" + productId, exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Shop-Internal-Key"));
            byte[] body = "{\"data\":{\"soundUrl\":\"https://cdn.test/sound.mp3\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new ShopSoundClient("http://127.0.0.1:" + server.getAddress().getPort(), 2000);
            ReflectionTestUtils.setField(client, "shopInternalKey", "sound-shop-test-key");
            assertThat(client.soundUrl(accountId, productId)).isEqualTo("https://cdn.test/sound.mp3");
            assertThat(key.get()).isEqualTo("sound-shop-test-key");
        } finally { server.stop(0); }
    }
}
