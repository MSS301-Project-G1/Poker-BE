package com.msspoker.gameservice.client;

import com.msspoker.gameservice.exception.GameApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ShopSoundErrorContractTest {
    private final UUID account = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();
    private HttpServer server;
    private ShopSoundClient client;
    private volatile int status;
    private volatile String body;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/inventory/" + account + "/sounds/" + product, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new ShopSoundClient("http://127.0.0.1:" + server.getAddress().getPort(), 2000);
        ReflectionTestUtils.setField(client, "shopInternalKey", "shop-private-test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void onlyConfirmedOwnershipRejectionIsReportedAsNotOwned() {
        status = 404;
        body = "{\"code\":\"SHOP_ITEM_NOT_OWNED\",\"message\":\"fixture\"}";
        assertThatThrownBy(() -> client.soundUrl(account, product)).isInstanceOfSatisfying(GameApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getMessage()).contains("chưa sở hữu");
        });
    }

    @Test
    void unplayableProductIsReportedAsBadInputRatherThanAnOwnershipFailure() {
        status = 400;
        body = "{\"code\":\"SHOP_SOUND_NOT_PLAYABLE\"}";
        assertThatThrownBy(() -> client.soundUrl(account, product)).isInstanceOfSatisfying(GameApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getMessage()).doesNotContain("chưa sở hữu");
        });
    }

    @Test
    void backendAuthenticationUnknownAndServerErrorsAreDependencyFailures() {
        for (int reply : List.of(400, 401, 403, 404, 429, 500, 503)) {
            status = reply;
            body = "{\"code\":\"SHOP_INTERNAL_FORBIDDEN\"}";
            unavailable();
        }
        status = 500;
        body = "{\"code\":\"SHOP_ITEM_NOT_OWNED\"}";
        unavailable();
        status = 404;
        body = "<html>route not found</html>";
        unavailable();
    }

    @Test
    void invalidSuccessPayloadsDoNotProducePlayableUrls() {
        status = 200;
        for (String reply : List.of("{}", "not-json", "{\"data\":{}}", "{\"data\":{\"soundUrl\":\"   \"}}")) {
            body = reply;
            unavailable();
        }
    }

    @Test
    void connectionFailureIsReportedAsUnavailable() {
        server.stop(0);
        unavailable();
    }

    private void unavailable() {
        assertThatThrownBy(() -> client.soundUrl(account, product)).isInstanceOfSatisfying(GameApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getMessage()).doesNotContain("chưa sở hữu");
        });
    }
}
