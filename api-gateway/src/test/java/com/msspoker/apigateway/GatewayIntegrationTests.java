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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTests {
    private static final String ACCOUNT_ID = UUID.randomUUID().toString();
    private static final String ISSUER = "http://localhost:8090";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RSAKey KEY = key("auth-key");
    private static final RSAKey FOREIGN_KEY = key("foreign-key");
    private static final AtomicReference<ObservedRequest> LAST = new AtomicReference<>();
    private static final AtomicReference<Reply> REPLY = new AtomicReference<>();
    private static final DisposableServer UPSTREAM = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                if (request.uri().equals("/.well-known/jwks.json")) {
                    return response.header(HttpHeaders.CONTENT_TYPE, "application/json")
                            .sendString(Mono.just(JSON.writeValueAsString(new JWKSet(KEY.toPublicJWK()).toJSONObject())));
                }
                return request.receive().aggregate().asString().defaultIfEmpty("").flatMap(body -> {
                    var headers = new java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
                    request.requestHeaders().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
                    var observed = new ObservedRequest(request.uri(), request.method().name(), body, headers);
                    LAST.set(observed);
                    if (request.uri().equals("/api/auth/slow-test")) {
                        return Mono.delay(Duration.ofSeconds(4)).then(response.sendString(Mono.just("{}")).then());
                    }
                    var reply = REPLY.get();
                    response.header(HttpHeaders.CONTENT_TYPE, "application/json");
                    if (request.requestHeaders().contains("Origin")) {
                        response.addHeader("Access-Control-Allow-Origin", request.requestHeaders().get("Origin"));
                        response.addHeader("Access-Control-Allow-Credentials", "true");
                    }
                    if (reply != null) {
                        response.status(reply.status());
                        reply.cookies().forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));
                    }
                    return response.sendString(Mono.just(reply == null
                            ? JSON.writeValueAsString(Map.of("path", observed.uri(), "method", observed.method(), "ok", true))
                            : reply.body())).then();
                });
            }).bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String upstream = "http://127.0.0.1:" + UPSTREAM.port();
        registry.add("AUTH_SERVICE_URL", () -> upstream);
        registry.add("GAME_SERVICE_URL", () -> upstream);
        registry.add("GAME_SERVICE_KEY", () -> "gateway-game-test-secret");
        registry.add("SOCIAL_SERVICE_URL", () -> upstream);
        registry.add("CHAT_SERVICE_URL", () -> upstream);
        registry.add("SOCIAL_GATEWAY_KEY", () -> "social-gateway-test-key");
        registry.add("CHAT_GATEWAY_KEY", () -> "chat-gateway-test-key");
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "2s");
        registry.add("MATCHMAKING_SERVICE_URL", () -> "http://127.0.0.1:" + unusedPort());
    }

    @org.springframework.beans.factory.annotation.Autowired
    Environment environment;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        LAST.set(null);
        REPLY.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + environment.getProperty("local.server.port"))
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    @Test
    void socialAndChatPreservePathsAndReplaceSpoofedIngressKeys() {
        for (String path : List.of("/api/friends", "/api/blocks", "/api/chat/conversations")) {
            client.get().uri(path).cookie("accessToken", token("USER", "valid"))
                    .header("X-User-Id", UUID.randomUUID().toString())
                    .header("X-Social-Gateway-Key", "forged")
                    .header("X-Chat-Gateway-Key", "forged")
                    .header("X-Social-Internal-Key", "forged")
                    .exchange().expectStatus().isOk();
            assertThat(LAST.get().uri()).isEqualTo(path);
            assertThat(LAST.get().headers().get("X-User-Id")).isEqualTo(ACCOUNT_ID);
            assertThat(LAST.get().headers()).doesNotContainKey("X-Social-Internal-Key");
            if (path.startsWith("/api/chat")) {
                assertThat(LAST.get().headers().get("X-Chat-Gateway-Key")).isEqualTo("chat-gateway-test-key");
                assertThat(LAST.get().headers()).doesNotContainKey("X-Social-Gateway-Key");
            } else {
                assertThat(LAST.get().headers().get("X-Social-Gateway-Key")).isEqualTo("social-gateway-test-key");
                assertThat(LAST.get().headers()).doesNotContainKey("X-Chat-Gateway-Key");
            }
        }
    }

    @AfterAll
    static void closeUpstream() { UPSTREAM.disposeNow(); }

    @Test
    void publicRegistrationPathsPreserveMethodBodyQueryAndIgnoreExpiredAccessCookies() throws Exception {
        for (String path : List.of("/api/auth/register", "/api/auth/register/verify-otp", "/api/auth/register/resend-otp")) {
            String body = "{\"email\":\"khanh@example.com\",\"otp\":\"012345\"}";
            client.post().uri(path + "?trace=example").cookie("accessToken", token("USER", "expired"))
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isOk();
            assertThat(LAST.get().uri()).isEqualTo(path + "?trace=example");
            assertThat(LAST.get().method()).isEqualTo("POST");
            assertThat(LAST.get().body()).isEqualTo(body);
            assertThat(LAST.get().headers()).doesNotContainKeys("X-User-Id", "X-User-Role");
        }
    }

    @Test
    void cookieAndCsrfContractSurvivesLoginMeRefreshAndLogout() throws Exception {
        REPLY.set(new Reply(200, "{\"result\":{\"token\":\"csrf-example\"}}",
                List.of("XSRF-TOKEN=csrf-example; Path=/; HttpOnly; SameSite=Lax")));
        client.get().uri("/api/auth/csrf").exchange().expectStatus().isOk()
                .expectCookie().httpOnly("XSRF-TOKEN", true)
                .expectBody().jsonPath("$.result.token").isEqualTo("csrf-example");

        String access = token("USER", "valid");
        REPLY.set(new Reply(200, "{\"code\":\"success_request\"}", List.of(
                "accessToken=" + access + "; Path=/; HttpOnly; SameSite=Lax",
                "refreshToken=refresh-first; Path=/api/auth; HttpOnly; SameSite=Lax")));
        client.post().uri("/api/auth/login").cookie("XSRF-TOKEN", "csrf-example")
                .cookie("accessToken", "expired-or-malformed-cookie")
                .header("X-XSRF-TOKEN", "csrf-example").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"khanh@example.com\",\"password\":\"Poker123!\"}")
                .exchange().expectStatus().isOk().expectCookie().valueEquals("accessToken", access)
                .expectCookie().path("refreshToken", "/api/auth").expectCookie().httpOnly("refreshToken", true);
        assertThat(LAST.get().headers().get("X-XSRF-TOKEN")).isEqualTo("csrf-example");

        REPLY.set(null);
        client.get().uri("/api/auth/me").cookie("accessToken", access).cookie("XSRF-TOKEN", "csrf-example")
                .exchange().expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
        assertThat(LAST.get().uri()).isEqualTo("/api/auth/me");
        assertThat(LAST.get().headers().get("X-User-Id")).isEqualTo(ACCOUNT_ID);

        String rotated = token("USER", "valid");
        REPLY.set(new Reply(200, "{\"code\":\"success_request\"}", List.of(
                "accessToken=" + rotated + "; Path=/; HttpOnly; SameSite=Lax",
                "refreshToken=refresh-next; Path=/api/auth; HttpOnly; SameSite=Lax")));
        client.post().uri("/api/auth/refresh").cookie("accessToken", token("USER", "expired"))
                .cookie("refreshToken", "refresh-first").cookie("XSRF-TOKEN", "csrf-example")
                .header("X-XSRF-TOKEN", "csrf-example").exchange().expectStatus().isOk()
                .expectCookie().valueEquals("accessToken", rotated).expectCookie().valueEquals("refreshToken", "refresh-next");
        assertThat(LAST.get().headers().get("Cookie")).contains("refreshToken=refresh-first");

        REPLY.set(new Reply(200, "{\"code\":\"success_request\"}", List.of(
                "accessToken=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax",
                "refreshToken=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax")));
        client.post().uri("/api/auth/logout").cookie("refreshToken", "refresh-next")
                .cookie("XSRF-TOKEN", "csrf-example").header("X-XSRF-TOKEN", "csrf-example")
                .exchange().expectStatus().isOk().expectCookie().maxAge("accessToken", Duration.ZERO)
                .expectCookie().maxAge("refreshToken", Duration.ZERO).expectCookie().doesNotExist("XSRF-TOKEN");
    }

    @Test
    void authErrorsArePassedThroughWithoutClearingCookies() {
        REPLY.set(new Reply(403, "{\"code\":\"AUTH_CSRF_INVALID\"}", List.of()));
        client.post().uri("/api/auth/logout").exchange().expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_CSRF_INVALID");
        REPLY.set(new Reply(401, "{\"code\":\"AUTH_REFRESH_INVALID\"}", List.of()));
        client.post().uri("/api/auth/refresh").exchange().expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody().jsonPath("$.code").isEqualTo("AUTH_REFRESH_INVALID");
    }

    @Test
    void meAndWebsocketTokenRequireAnAccessCookieAndIgnoreBearerHeaders() {
        for (String path : List.of("/api/auth/me", "/api/auth/ws-token", "/api/auth/not-public")) {
            client.get().uri(path).exchange().expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_UNAUTHENTICATED");
        }
        client.get().uri("/api/auth/me").header("Authorization", "Bearer " + token("USER", "valid"))
                .exchange().expectStatus().isUnauthorized();
        assertThat(LAST.get()).isNull();
    }

    @Test
    void invalidJwtSignatureIssuerAudienceTimesSubjectAndRoleAreRejected() {
        for (String variant : List.of("signature", "issuer", "audience", "expired", "missing-expiry", "subject",
                "role", "future-iat", "future-nbf", "missing-iat", "missing-nbf", "missing-jti")) {
            client.get().uri("/api/auth/me").cookie("accessToken", token("USER", variant))
                    .exchange().expectStatus().isUnauthorized().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                    .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_UNAUTHENTICATED");
        }
        assertThat(LAST.get()).isNull();
    }

    @Test
    void identityAndServiceHeadersAreReplacedWithVerifiedValues() {
        client.get().uri("/api/game/me/active-table").cookie("accessToken", token("USER", "valid"))
                .header("X-User-Id", UUID.randomUUID().toString()).header("X-User-Role", "ADMIN")
                .header("X-Game-Service-Key", "client-secret").header("X-Dev-User", "fake")
                .header("X-Request-Id", "client-selected-id").exchange().expectStatus().isOk();
        var request = LAST.get();
        assertThat(request.uri()).isEqualTo("/api/game/me/active-table");
        assertThat(request.headers().get("X-User-Id")).isEqualTo(ACCOUNT_ID);
        assertThat(request.headers().get("X-User-Role")).isEqualTo("USER");
        assertThat(request.headers().get("X-Game-Service-Key")).isEqualTo("gateway-game-test-secret");
        assertThat(request.headers()).doesNotContainKey("X-Dev-User");
        assertThat(UUID.fromString(request.headers().get("X-Request-Id"))).isNotNull();
    }

    @Test
    void publicRequestsCannotForwardSpoofedIdentityOrServiceCredentials() {
        client.get().uri("/api/auth/csrf").header("X-User-Id", ACCOUNT_ID).header("X-User-Role", "ADMIN")
                .header("X-Game-Service-Key", "fake-secret").header("X-Dev-User", "fake")
                .exchange().expectStatus().isOk();
        assertThat(LAST.get().headers()).doesNotContainKeys("X-User-Id", "X-User-Role", "X-Game-Service-Key", "X-Dev-User");
    }

    @Test
    void adminRoutesRequireAdminAndPreserveTheGameAdminPath() {
        client.get().uri("/api/admin/match-settings").cookie("accessToken", token("USER", "valid"))
                .exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("GATEWAY_FORBIDDEN");
        assertThat(LAST.get()).isNull();
        client.get().uri("/api/admin/match-settings").cookie("accessToken", token("ADMIN", "valid"))
                .exchange().expectStatus().isOk();
        assertThat(LAST.get().uri()).isEqualTo("/api/admin/match-settings");
        assertThat(LAST.get().headers().get("X-User-Role")).isEqualTo("ADMIN");
    }

    @Test
    void nonAuthWritesRequireTheSameCsrfCookieAndHeaderWithoutIssuingAnotherCookie() {
        String access = token("ADMIN", "valid");
        client.put().uri("/api/admin/match-settings/RANK").cookie("accessToken", access)
                .exchange().expectStatus().isForbidden().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_CSRF_INVALID");
        client.put().uri("/api/admin/match-settings/RANK").cookie("accessToken", access)
                .cookie("XSRF-TOKEN", "csrf-example").header("X-XSRF-TOKEN", "wrong")
                .exchange().expectStatus().isForbidden();
        assertThat(LAST.get()).isNull();
        client.put().uri("/api/admin/match-settings/RANK").cookie("accessToken", access)
                .cookie("XSRF-TOKEN", "csrf-example").header("X-XSRF-TOKEN", "csrf-example")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"smallBlind\":10}")
                .exchange().expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);
        assertThat(LAST.get().method()).isEqualTo("PUT");
        assertThat(LAST.get().uri()).isEqualTo("/api/admin/match-settings/RANK");
    }

    @Test
    void internalEndpointsAreBlockedForAnonymousAndAdmin() {
        client.get().uri("/internal/accounts/" + ACCOUNT_ID + "/restrictions").exchange().expectStatus().isForbidden();
        client.get().uri("/internal/accounts/" + ACCOUNT_ID + "/restrictions")
                .cookie("accessToken", token("ADMIN", "valid")).exchange().expectStatus().isForbidden();
        assertThat(LAST.get()).isNull();
    }

    @Test
    void corsPreflightAndActualRequestsHaveOneCredentialedOriginHeader() {
        client.options().uri("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
        client.get().uri("/api/auth/csrf").header("Origin", "http://localhost:5173")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void untrustedOriginsAndCrossSiteRequestsAreRejectedBeforeProxying() {
        client.post().uri("/api/auth/login").header("Origin", "https://untrusted.example")
                .exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("GATEWAY_ORIGIN_NOT_ALLOWED");
        client.post().uri("/api/auth/logout").header("Sec-Fetch-Site", "cross-site")
                .exchange().expectStatus().isForbidden();
        assertThat(LAST.get()).isNull();
    }

    @Test
    void jwksAndSwaggerArePublicAndKeepTheirPaths() {
        client.get().uri("/.well-known/jwks.json").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.keys[0].kid").isEqualTo("auth-key").jsonPath("$.keys[0].d").doesNotExist();
        for (String path : List.of("/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config")) {
            client.get().uri(path).exchange().expectStatus().isOk();
            assertThat(LAST.get().uri()).isEqualTo(path);
        }
    }

    @Test
    void unreachableUpstreamReturnsServiceUnavailableWithRequestId() {
        client.get().uri("/api/matchmaking/queue").cookie("accessToken", token("USER", "valid"))
                .exchange().expectStatus().isEqualTo(503).expectHeader().exists("X-Request-Id")
                .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_UPSTREAM_UNAVAILABLE")
                .jsonPath("$.timestamp").exists().jsonPath("$.requestId").exists();
    }

    @Test
    void slowUpstreamReturnsGatewayTimeoutWithTheCommonErrorFormat() {
        client.get().uri("/api/auth/slow-test").cookie("accessToken", token("USER", "valid"))
                .exchange().expectStatus().isEqualTo(504)
                .expectBody().jsonPath("$.code").isEqualTo("GATEWAY_UPSTREAM_TIMEOUT")
                .jsonPath("$.requestId").exists();
    }

    private static RSAKey key(String kid) {
        try { return new RSAKeyGenerator(2048).keyID(kid).generate(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String token(String role, String variant) {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(variant.equals("issuer") ? "https://other.example" : ISSUER)
                .audience(variant.equals("audience") ? "other" : "poker")
                .subject(variant.equals("subject") ? "invalid-uuid" : ACCOUNT_ID)
                .claim("role", variant.equals("role") ? "SUPERUSER" : role);
        if (!variant.equals("missing-expiry")) claims.expirationTime(Date.from(now.plusSeconds(variant.equals("expired") ? -3600 : 900)));
        if (!variant.equals("missing-iat")) claims.issueTime(Date.from(now.plusSeconds(variant.equals("future-iat") ? 3600 : -7200)));
        if (!variant.equals("missing-nbf")) claims.notBeforeTime(Date.from(now.plusSeconds(variant.equals("future-nbf") ? 3600 : -7200)));
        if (!variant.equals("missing-jti")) claims.jwtID(UUID.randomUUID().toString());
        try {
            // Same kid with a foreign signature must fail even when the expected key is cached.
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(variant.equals("signature") ? FOREIGN_KEY : KEY));
            return jwt.serialize();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static int unusedPort() {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    record ObservedRequest(String uri, String method, String body, Map<String, String> headers) { }
    record Reply(int status, String body, List<String> cookies) { }
}
