package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.response.RankedPlayerResponse;
import com.msspoker.competitionservice.enums.CompetitionError;
import com.msspoker.competitionservice.exception.CompetitionApiException;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real client beans resolve service names through Spring Cloud LoadBalancer. Eureka is off in
 * tests, so ranking-service is registered as a simple discovery instance pointing at a stub server.
 */
@SpringBootTest(properties = "competition.social.mock=false")
@ActiveProfiles("test")
class LoadBalancedClientTest {
    static final UUID TOP_PLAYER = UUID.randomUUID();
    static final AtomicReference<String> requested = new AtomicReference<>();
    static final HttpServer ranking = start();

    @Autowired RankingClient rankingClient;
    @Autowired SocialClient socialClient;
    @Autowired ObjectProvider<RestClient.Builder> builders;

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.ranking-service[0].uri",
                () -> "http://127.0.0.1:" + ranking.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ranking.stop(0);
    }

    @Test
    void serviceNamesResolveToRegisteredInstances() {
        List<RankedPlayerResponse> top = rankingClient.top(1);
        assertEquals(List.of(TOP_PLAYER), top.stream().map(RankedPlayerResponse::accountId).toList());
        assertEquals("/internal/ranking/top?limit=1", requested.get());
    }

    @Test
    void unqualifiedBuildersCallAddressesDirectly() {
        // The Eureka client asks for a builder exactly like this; a load-balanced one could not reach the registry.
        String body = builders.getIfAvailable(RestClient::builder).build().get()
                .uri("http://127.0.0.1:" + ranking.getAddress().getPort() + "/internal/ranking/top?limit=1")
                .retrieve().body(String.class);
        assertTrue(body.contains(TOP_PLAYER.toString()));
    }

    @Test
    void servicesWithoutInstancesAreDependencyErrors() {
        CompetitionApiException ex = assertThrows(CompetitionApiException.class,
                () -> socialClient.friendship(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(CompetitionError.COMP_DEPENDENCY_UNAVAILABLE, ex.getCode());
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/ranking/top", exchange -> {
                requested.set(exchange.getRequestURI().toString());
                byte[] body = ("[{\"position\":1,\"accountId\":\"" + TOP_PLAYER
                        + "\",\"elo\":1700,\"tier\":\"CARD_GOD\",\"winStreak\":0,\"matchesPlayed\":30}]")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
