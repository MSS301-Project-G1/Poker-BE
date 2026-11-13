package com.msspoker.matchmakingservice.service.impl.client.http;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRankingClientContractTest {
    private final UUID account = UUID.randomUUID();
    private final AtomicReference<String> internalKey = new AtomicReference<>();
    private HttpServer server;
    private int status = 200;
    private HttpRankingClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/ranking/" + account, exchange -> {
            internalKey.set(exchange.getRequestHeaders().getFirst("X-Ranking-Internal-Key"));
            String body = status == 200 ? "{\"accountId\":\"" + account + "\",\"elo\":350,\"winStreak\":2,\"lossStreak\":0}"
                    : "{\"code\":\"RANK_INTERNAL_FORBIDDEN\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new HttpRankingClient("http://127.0.0.1:" + server.getAddress().getPort(), new MatchmakingProperties());
        ReflectionTestUtils.setField(client, "rankingInternalKey", "ranking-internal-test-key");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsInternalKeyAndKeepsThePlayerRankingContract() {
        var ranking = client.ranking(account);
        assertThat(internalKey.get()).isEqualTo("ranking-internal-test-key");
        assertThat(ranking.accountId()).isEqualTo(account);
        assertThat(ranking.elo()).isEqualTo(350);
        assertThat(ranking.winStreak()).isEqualTo(2);
    }

    @Test
    void rejectedKeyIsReportedAsDependencyFailure() {
        status = 403;
        assertThatThrownBy(() -> client.ranking(account)).isInstanceOfSatisfying(MatchmakingApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(MatchmakingError.MM_DEPENDENCY_UNAVAILABLE);
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        });
    }
}
