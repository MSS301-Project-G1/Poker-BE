package com.msspoker.apigateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.register-with-eureka=false", "eureka.client.disable-delta=true",
        "spring.cloud.loadbalancer.cache.enabled=false"})
class GatewayDiscoveryTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RSAKey KEY = key();
    private static final String ACCOUNT = UUID.randomUUID().toString();
    private static final AtomicReference<Map<String, String>> LAST = new AtomicReference<>();
    private static final DisposableServer SERVICE = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                LAST.set(Map.of("path", request.uri(), "user", request.requestHeaders().get("X-User-Id", ""),
                        "role", request.requestHeaders().get("X-User-Role", "")));
                return response.header("Content-Type", "application/json").sendString(Mono.just("{\"owner\":\"fixture\"}"));
            }).bindNow();
    private static final DisposableServer REGISTRY = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> response.header("Content-Type", "application/json")
                    .sendString(Mono.just(request.uri().contains("jwks")
                            ? JSON.writeValueAsString(new JWKSet(KEY.toPublicJWK()).toJSONObject()) : registryJson())))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("EUREKA_SERVICE_URL", () -> "http://127.0.0.1:" + REGISTRY.port() + "/eureka/");
        registry.add("GATEWAY_JWK_SET_URI", () -> "http://127.0.0.1:" + REGISTRY.port() + "/jwks");
        // An owner's new service and security rules are configuration, without Java route changes.
        String service = "gateway.catalog.services.fixture-service.";
        registry.add(service + "uri", () -> "lb://fixture-service");
        registry.add(service + "routes[0].id", () -> "fixture-service");
        registry.add(service + "routes[0].paths[0]", () -> "/api/fixture/**");
        registry.add(service + "public-endpoints[0].method", () -> "GET");
        registry.add(service + "public-endpoints[0].paths[0]", () -> "/api/fixture/public");
        registry.add(service + "admin-endpoints[0].method", () -> "GET");
        registry.add(service + "admin-endpoints[0].paths[0]", () -> "/api/fixture/manage");
    }

    @Autowired Environment environment;
    private WebTestClient client;

    @BeforeEach
    void setup() {
        LAST.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + environment.getProperty("local.server.port"))
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void close() {
        REGISTRY.disposeNow();
        SERVICE.disposeNow();
    }

    @Test
    void discoversServiceUsingEurekaAndForwardsVerifiedIdentityWithOriginalPath() {
        client.get().uri("/api/fixture/me?view=full").cookie("accessToken", token("USER"))
                .header("X-User-Id", "spoofed").header("X-User-Role", "ADMIN")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.owner").isEqualTo("fixture");
        assertThat(LAST.get()).containsEntry("path", "/api/fixture/me?view=full")
                .containsEntry("user", ACCOUNT).containsEntry("role", "USER");
    }

    @Test
    void ownerPublicAndAdminRulesAreAppliedWithoutJavaChanges() {
        client.get().uri("/api/fixture/public").exchange().expectStatus().isOk();
        assertThat(LAST.get().get("user")).isEmpty();
        client.get().uri("/api/fixture/me").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/fixture/manage").cookie("accessToken", token("USER"))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/fixture/manage").cookie("accessToken", token("ADMIN"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void discoveryDoesNotExposeInternalOrAutomaticServiceIdRoutesAndMissingInstanceReturns503() {
        client.get().uri("/internal/wallet/credit").cookie("accessToken", token("ADMIN"))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/fixture-service/api/fixture/public").cookie("accessToken", token("USER"))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/api/wallet/me").cookie("accessToken", token("USER"))
                .exchange().expectStatus().isEqualTo(503);
        assertThat(LAST.get()).isNull();
    }

    @Test
    void newlyConfiguredServiceWritesStillRequireCsrf() {
        String token = token("USER");
        client.post().uri("/api/fixture/action").cookie("accessToken", token)
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/fixture/action").cookie("accessToken", token).cookie("XSRF-TOKEN", "csrf")
                .header("X-XSRF-TOKEN", "csrf").exchange().expectStatus().isOk();
    }

    private static String registryJson() {
        Map<String, Object> instance = Map.ofEntries(
                Map.entry("instanceId", "fixture-1"), Map.entry("hostName", "127.0.0.1"),
                Map.entry("app", "FIXTURE-SERVICE"), Map.entry("ipAddr", "127.0.0.1"),
                Map.entry("status", "UP"), Map.entry("overriddenStatus", "UNKNOWN"),
                Map.entry("port", Map.of("$", SERVICE.port(), "@enabled", "true")),
                Map.entry("securePort", Map.of("$", 443, "@enabled", "false")),
                Map.entry("dataCenterInfo", Map.of("@class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo", "name", "MyOwn")),
                Map.entry("vipAddress", "fixture-service"), Map.entry("secureVipAddress", "fixture-service"),
                Map.entry("metadata", Map.of()));
        return JSON.writeValueAsString(Map.of("applications", Map.of("versions__delta", "1", "apps__hashcode", "UP_1_",
                "application", List.of(Map.of("name", "FIXTURE-SERVICE", "instance", List.of(instance))))));
    }

    private static String token(String role) {
        try {
            Instant now = Instant.now();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                    new JWTClaimsSet.Builder().subject(ACCOUNT).issuer("http://localhost:8090").audience("poker")
                            .claim("role", role).issueTime(Date.from(now)).notBeforeTime(Date.from(now))
                            .expirationTime(Date.from(now.plusSeconds(300))).jwtID(UUID.randomUUID().toString()).build());
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("discovery-test").generate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
