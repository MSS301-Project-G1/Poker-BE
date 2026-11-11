package com.msspoker.apigateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
class GatewaySwaggerTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> PATHS = Map.of(
            "auth-service", "/api/auth/me", "game-service", "/api/game/me/active-table",
            "matchmaking-service", "/api/party/me", "social-service", "/api/friends",
            "chat-service", "/api/chat/conversations", "ranking-service", "/api/ranking/leaderboard",
            "competition-service", "/api/tournaments", "shop-service", "/api/shop/products", "wallet-service", "/api/wallet/me");
    private static final AtomicReference<String> FAILURE = new AtomicReference<>();
    private static final AtomicReference<Map<String, String>> LAST = new AtomicReference<>();
    private static final DisposableServer UPSTREAM = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                String service = request.uri().split("/")[1];
                LAST.set(Map.of("path", request.uri(), "cookie", request.requestHeaders().get("Cookie", ""),
                        "authorization", request.requestHeaders().get("Authorization", ""),
                        "key", request.requestHeaders().get("X-Game-Service-Key", "")));
                if ("down".equals(FAILURE.get())) return response.status(500).send();
                if ("invalid".equals(FAILURE.get())) return response.header("Content-Type", "application/json").sendString(Mono.just("{}"));
                return response.header("Content-Type", "application/json").sendString(Mono.just(JSON.writeValueAsString(Map.of(
                        "openapi", "3.1.0", "info", Map.of("title", service, "version", "1"),
                        "servers", List.of(Map.of("url", "http://private-service:8090")),
                        "components", Map.of("schemas", Map.of("Fixture", Map.of("type", "object")),
                                "securitySchemes", Map.of("internal", Map.of("type", "apiKey", "in", "header", "name", "X-Wallet-Internal-Key"))),
                        "paths", Map.of(PATHS.get(service), Map.of(
                                "servers", List.of(Map.of("url", "http://private-path")),
                                "parameters", List.of(Map.of("name", "X-User-Id", "in", "header")),
                                "get", Map.of("servers", List.of(Map.of("url", "http://private-operation")),
                                        "parameters", List.of(Map.of("name", "X-Wallet-Internal-Key", "in", "header"), Map.of("name", "page", "in", "query")),
                                        "security", List.of(Map.of("internal", List.of())), "responses", Map.of("200", Map.of("description", "OK")))),
                                "/internal/wallet/credit", Map.of("post", Map.of("responses", Map.of())),
                                "/api/not-in-the-catalog", Map.of("get", Map.of("responses", Map.of())))))));
            }).bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PATHS.keySet().forEach(service -> registry.add(service.replace('-', '_').toUpperCase() + "_URL",
                () -> "http://127.0.0.1:" + UPSTREAM.port() + "/" + service));
        registry.add("GAME_SERVICE_KEY", () -> "swagger-game-fixture-key");
    }

    @Autowired Environment environment;
    private WebTestClient client;

    @BeforeEach
    void setup() {
        FAILURE.set(null);
        LAST.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + environment.getProperty("local.server.port"))
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll static void close() { UPSTREAM.disposeNow(); }

    @Test
    void servesLocalUiAndAllNineCatalogDefinitionsWithoutContactingAuth() {
        client.get().uri("/swagger-ui.html").exchange().expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html");
        client.get().uri("/swagger-ui/index.html").cookie("accessToken", "invalid-stale-cookie")
                .exchange().expectStatus().isOk().expectBody(String.class).value(body -> assertThat(body).contains("Poker API Gateway"));
        for (String asset : List.of("swagger-ui-bundle.js", "swagger-ui-standalone-preset.js", "swagger-ui.css", "swagger-initializer.js")) {
            client.get().uri("/swagger-ui/" + asset).exchange().expectStatus().isOk();
        }
        client.get().uri("/v3/api-docs/swagger-config").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.urls.length()").isEqualTo(9).jsonPath("$.urls.primaryName").doesNotExist()
                .jsonPath("$['urls.primaryName']").isEqualTo("auth-service");
        assertThat(LAST.get()).isNull();
    }

    @Test
    void fetchesEachServiceAndRewritesEveryServerToTheGateway() {
        for (String service : PATHS.keySet()) {
            client.get().uri("/v3/api-docs/" + service).cookie("accessToken", "stale")
                    .header("Authorization", "Bearer spoofed").exchange().expectStatus().isOk().expectBody()
                    .jsonPath("$.info.title").isEqualTo(service)
                    .jsonPath("$.servers[0].url").isEqualTo("/")
                    .jsonPath("$.paths['" + PATHS.get(service) + "'].servers").doesNotExist()
                    .jsonPath("$.paths['" + PATHS.get(service) + "'].get.servers").doesNotExist()
                    .jsonPath("$.components.schemas.Fixture.type").isEqualTo("object");
            assertThat(LAST.get()).containsEntry("path", "/" + service + "/v3/api-docs")
                    .containsEntry("cookie", "").containsEntry("authorization", "");
            assertThat(LAST.get().get("key")).isEqualTo(service.equals("game-service") ? "swagger-game-fixture-key" : "");
        }
    }

    @Test
    void stripsInternalUnroutedEndpointsAndIdentityInputsWhileKeepingSchemasAndQueryParameters() {
        client.get().uri("/v3/api-docs/wallet-service").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths.length()").isEqualTo(1)
                .jsonPath("$.paths['/internal/wallet/credit']").doesNotExist()
                .jsonPath("$.paths['/api/not-in-the-catalog']").doesNotExist()
                .jsonPath("$.paths['/api/wallet/me'].parameters.length()").isEqualTo(0)
                .jsonPath("$.paths['/api/wallet/me'].get.parameters[0].name").isEqualTo("page")
                .jsonPath("$.paths['/api/wallet/me'].get.security[0].cookieAuth").isArray()
                .jsonPath("$.components.securitySchemes.internal").doesNotExist();
        client.get().uri("/v3/api-docs/shop-service").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.paths['/api/shop/products'].get.security.length()").isEqualTo(0);
        client.post().uri("/internal/wallet/credit").exchange().expectStatus().isForbidden();
        client.get().uri("/api/wallet/me").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownDisabledAndUnavailableDefinitionsFailWithoutFallingBackToAuth() {
        for (String service : List.of("unknown-service", "identity-service")) {
            client.get().uri("/v3/api-docs/" + service).exchange().expectStatus().isNotFound();
        }
        assertThat(LAST.get()).isNull();
        for (String failure : List.of("down", "invalid")) {
            FAILURE.set(failure);
            client.get().uri("/v3/api-docs/wallet-service").exchange().expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE");
        }
    }
}
