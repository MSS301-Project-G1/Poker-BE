package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, LoginSessionTests.TimeConfiguration.class})
class LoginSessionTests {
    private static final Instant NOW = Instant.parse("2026-10-10T01:00:00Z");
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthService service;
    @Autowired private JwtService jwtService;
    @Autowired private RSAKey signingKey;
    @Autowired private JsonMapper mapper;
    @Autowired private MutableClock clock;
    @Autowired private PlatformTransactionManager transactionManager;
    private MockMvc mvc;
    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void setup() throws Exception {
        clock.set(NOW);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        MvcResult csrf = mvc.perform(get("/api/auth/csrf")).andReturn();
        assertThat(csrf.getResponse().getStatus()).isEqualTo(200);
        csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        csrfToken = result(csrf).get("token").asString();
    }

    @Test
    void loginSetsPersistentHttpOnlyCookiesAndStoresOnlyAHashWithPublicJwks() throws Exception {
        Account account = account(true);
        MvcResult login = login(account.getEmail().toUpperCase(), "Poker123!", null);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getHeader("Cache-Control")).contains("no-store");
        Cookie access = cookie(login, "accessToken");
        Cookie refresh = cookie(login, "refreshToken");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isFalse();
        assertThat(access.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(refresh.getPath()).isEqualTo("/api/auth");
        assertThat(access.getMaxAge()).isEqualTo(900);
        assertThat(refresh.getMaxAge()).isEqualTo(604800);
        assertThat(result(login).get("accountId").asString()).isEqualTo(account.getId().toString());
        assertThat(result(login).has("accessToken")).isFalse();
        assertThat(result(login).has("refreshToken")).isFalse();
        var stored = refreshTokens.findByTokenHash(hash(refresh.getValue())).orElseThrow();
        assertThat(stored.getId().version()).isEqualTo(7);
        assertThat(stored.getTokenHash()).isNotEqualTo(refresh.getValue());
        assertThat(stored.getAccountId()).isEqualTo(account.getId());
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plusSeconds(604800));
        var jwt = jwtService.verify(access.getValue());
        assertThat(jwt.getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://localhost:8090");
        assertThat(jwt.getAudience()).contains("poker");
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        String jwks = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        RSAKey publicKey = (RSAKey) JWKSet.parse(jwks).getKeys().getFirst();
        assertThat(publicKey.isPrivate()).isFalse();
        assertThat(publicKey.getKeyID()).isEqualTo(signingKey.getKeyID());
        assertThat(SignedJWT.parse(access.getValue()).verify(new com.nimbusds.jose.crypto.RSASSAVerifier(publicKey))).isTrue();
    }

    @Test
    void wrongUnknownAndDeletedAccountsFailWhilePendingAccountMustVerifyAndLoginIsNotThrottled() throws Exception {
        Account account = account(true);
        for (int i = 0; i < 6; i++) {
            error(login(account.getEmail(), "wrong-password", null), 401, "AUTH_INVALID_CREDENTIALS");
        }
        error(login("unknown-" + UUID.randomUUID() + "@example.com", "Poker123!", null), 401, "AUTH_INVALID_CREDENTIALS");
        Account pending = account(false);
        error(login(pending.getEmail(), "Poker123!", null), 409, "AUTH_EMAIL_VERIFICATION_PENDING");
        account.setDeleted(true);
        accounts.saveAndFlush(account);
        error(login(account.getEmail(), "Poker123!", null), 401, "AUTH_INVALID_CREDENTIALS");
        account.setDeleted(false);
        accounts.saveAndFlush(account);
        assertThat(login(account.getEmail(), "Poker123!", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void meRequiresVerifiedAccessCookieAndDoesNotTrustUserHeaders() throws Exception {
        Account account = account(true);
        error(mvc.perform(get("/api/auth/me").header("X-User-Id", account.getId()).header("X-User-Role", "ADMIN")).andReturn(),
                401, "AUTH_UNAUTHENTICATED");
        MvcResult login = login(account.getEmail(), "Poker123!", null);
        MvcResult me = mvc.perform(get("/api/auth/me").cookie(cookie(login, "accessToken"))).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(result(me).get("profile").get("displayName").asString()).isEqualTo("Khanh");
        error(mvc.perform(get("/api/auth/me").cookie(new Cookie("accessToken", "forged"))).andReturn(), 401, "AUTH_UNAUTHENTICATED");
    }

    @Test
    void authenticatedRequestsKeepTheCsrfCookieForRefreshAndLogout() throws Exception {
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getCookie("XSRF-TOKEN")).isNull();
        Cookie access = cookie(login, "accessToken");
        Cookie refresh = cookie(login, "refreshToken");

        // A browser applies every Set-Cookie, including a deletion on an otherwise successful GET.
        for (String path : List.of("/me", "/me", "/ws-token", "/csrf")) {
            MvcResult response = mvc.perform(get("/api/auth" + path)
                    .cookie(csrfCookie, access, refresh)).andReturn();
            assertThat(response.getResponse().getStatus()).as(path).isEqualTo(200);
            assertThat(response.getResponse().getCookie("XSRF-TOKEN"))
                    .as("%s must not replace or delete the CSRF cookie", path).isNull();
            assertThat(response.getRequest().getSession(false)).as(path).isNull();
            if (path.equals("/csrf")) {
                assertThat(result(response).get("token").asString()).isEqualTo(csrfToken);
            }
        }

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh")
                .cookie(csrfCookie, access, refresh).header("X-XSRF-TOKEN", csrfToken)).andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(refreshed.getResponse().getCookie("XSRF-TOKEN")).isNull();

        Cookie rotatedRefresh = cookie(refreshed, "refreshToken");
        MvcResult logout = mvc.perform(post("/api/auth/logout")
                .cookie(csrfCookie, cookie(refreshed, "accessToken"), rotatedRefresh)
                .header("X-XSRF-TOKEN", csrfToken)).andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(logout.getResponse().getCookie("XSRF-TOKEN")).isNull();
        assertThat(refreshTokens.findByTokenHash(hash(rotatedRefresh.getValue())).orElseThrow().getRevokedAt())
                .isNotNull();
    }

    @Test
    void refreshRotatesBothTokensRejectsOldTokenAndPreservesOriginalDeadline() throws Exception {
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        Cookie oldRefresh = cookie(login, "refreshToken");
        clock.set(NOW.plusSeconds(600));
        MvcResult refreshed = postWithCsrf("/refresh", oldRefresh);
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(refreshed, "refreshToken").getValue()).isNotEqualTo(oldRefresh.getValue());
        assertThat(cookie(refreshed, "accessToken").getValue()).isNotEqualTo(cookie(login, "accessToken").getValue());
        assertThat(result(refreshed).get("refreshExpiresAt").asString()).isEqualTo("2026-10-17T01:00:00Z");
        assertThat(cookie(refreshed, "refreshToken").getMaxAge()).isEqualTo(604200);
        assertThat(refreshTokens.findByTokenHash(hash(oldRefresh.getValue()))).isEmpty();
        MvcResult replay = postWithCsrf("/refresh", oldRefresh);
        error(replay, 401, "AUTH_REFRESH_INVALID");
        // A losing concurrent request must not erase cookies returned by the winning refresh.
        assertThat(replay.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void accessExpiresExactlyAtDeadlineButRefreshWorksWithoutValidAccess() throws Exception {
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        clock.set(NOW.plusSeconds(900));
        error(mvc.perform(get("/api/auth/me").cookie(cookie(login, "accessToken"))).andReturn(), 401, "AUTH_UNAUTHENTICATED");
        MvcResult refresh = mvc.perform(post("/api/auth/refresh")
                .cookie(csrfCookie, cookie(login, "accessToken"), cookie(login, "refreshToken"))
                .header("X-XSRF-TOKEN", csrfToken)).andReturn();
        assertThat(refresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/auth/me").cookie(cookie(refresh, "accessToken")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshExpiresAtSevenDaysAndAccessNeverOutlivesRemainingSession() throws Exception {
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        clock.set(NOW.plusSeconds(604790));
        MvcResult lastRefresh = postWithCsrf("/refresh", cookie(login, "refreshToken"));
        assertThat(lastRefresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(lastRefresh, "accessToken").getMaxAge()).isEqualTo(10);
        clock.set(NOW.plusSeconds(604800));
        error(postWithCsrf("/refresh", cookie(lastRefresh, "refreshToken")), 401, "AUTH_REFRESH_INVALID");
    }

    @Test
    void logoutRevokesOnlyThisRefreshAndDeletesBothCookiesButIssuedAccessRemainsValid() throws Exception {
        Account account = account(true);
        MvcResult deviceA = login(account.getEmail(), "Poker123!", null);
        MvcResult deviceB = login(account.getEmail(), "Poker123!", null);
        Cookie refreshA = cookie(deviceA, "refreshToken");
        MvcResult logout = postWithCsrf("/logout", refreshA);
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(logout, "accessToken").getMaxAge()).isZero();
        assertThat(cookie(logout, "refreshToken").getMaxAge()).isZero();
        assertThat(cookie(logout, "accessToken").getPath()).isEqualTo("/");
        assertThat(cookie(logout, "refreshToken").getPath()).isEqualTo("/api/auth");
        assertThat(refreshTokens.findByTokenHash(hash(refreshA.getValue())).orElseThrow().getRevokedAt()).isEqualTo(NOW);
        error(postWithCsrf("/refresh", refreshA), 401, "AUTH_REFRESH_INVALID");
        assertThat(postWithCsrf("/refresh", cookie(deviceB, "refreshToken")).getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/auth/me").cookie(cookie(deviceA, "accessToken")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(postWithCsrf("/logout", refreshA).getResponse().getStatus()).isEqualTo(200);
        assertThat(postWithCsrf("/logout", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reloginReplacesBrowserRefreshWhileOtherDeviceRemainsLoggedIn() throws Exception {
        Account account = account(true);
        MvcResult first = login(account.getEmail(), "Poker123!", null);
        MvcResult otherDevice = login(account.getEmail(), "Poker123!", null);
        MvcResult replacement = login(account.getEmail(), "Poker123!", cookie(first, "refreshToken"));
        assertThat(replacement.getResponse().getStatus()).isEqualTo(200);
        error(postWithCsrf("/refresh", cookie(first, "refreshToken")), 401, "AUTH_REFRESH_INVALID");
        assertThat(postWithCsrf("/refresh", cookie(otherDevice, "refreshToken")).getResponse().getStatus()).isEqualTo(200);
        assertThat(postWithCsrf("/refresh", cookie(replacement, "refreshToken")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void twoConcurrentRefreshesCanRotateTheSameTokenOnlyOnce() throws Exception {
        String raw = cookie(login(account(true).getEmail(), "Poker123!", null), "refreshToken").getValue();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                try {
                    return service.refresh(raw) == null ? "unexpected" : "success";
                } catch (ApiException exception) {
                    return exception.getErrorCode().name();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "AUTH_REFRESH_INVALID");
        }
    }

    @Test
    void csrfIsRequiredForLoginRefreshAndLogoutAndFailedCheckDoesNotMutateSession() throws Exception {
        Account account = account(true);
        error(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(account.getEmail(), "Poker123!")))).andReturn(),
                403, "AUTH_CSRF_INVALID");
        MvcResult login = login(account.getEmail(), "Poker123!", null);
        Cookie refresh = cookie(login, "refreshToken");
        for (String path : List.of("/refresh", "/logout")) {
            error(mvc.perform(post("/api/auth" + path).cookie(refresh)).andReturn(), 403, "AUTH_CSRF_INVALID");
            error(mvc.perform(post("/api/auth" + path).cookie(refresh, csrfCookie)
                    .header("X-XSRF-TOKEN", "incorrect")).andReturn(), 403, "AUTH_CSRF_INVALID");
        }
        assertThat(refreshTokens.findByTokenHash(hash(refresh.getValue())).orElseThrow().getRevokedAt()).isNull();
        assertThat(postWithCsrf("/refresh", refresh).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void corsAllowsConfiguredFrontendWithCredentialsAndRejectsUntrustedOrigins() throws Exception {
        MvcResult allowed = mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token")).andReturn();
        assertThat(allowed.getResponse().getStatus()).isEqualTo(200);
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(allowed.getResponse().getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        MvcResult blocked = mvc.perform(post("/api/auth/logout").header("Origin", "https://untrusted.example")
                .cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken)).andReturn();
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void websocketTokenRequiresLoginAndReturnsTheExistingJwtForStomp() throws Exception {
        error(mvc.perform(get("/api/auth/ws-token")).andReturn(), 401, "AUTH_UNAUTHENTICATED");
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        MvcResult ws = mvc.perform(get("/api/auth/ws-token").cookie(cookie(login, "accessToken"))).andReturn();
        assertThat(ws.getResponse().getStatus()).isEqualTo(200);
        assertThat(result(ws).get("accessToken").asString()).isEqualTo(cookie(login, "accessToken").getValue());
        assertThat(ws.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void authenticatedUserCannotReachUndeclaredRoutesAndGetsTheCommonErrorFormat() throws Exception {
        MvcResult login = login(account(true).getEmail(), "Poker123!", null);
        error(mvc.perform(get("/api/auth/unpublished").cookie(cookie(login, "accessToken"))).andReturn(),
                403, "AUTH_FORBIDDEN");
    }

    @Test
    void deletedAccountCannotRefreshOrReadItsProfile() throws Exception {
        Account account = account(true);
        MvcResult login = login(account.getEmail(), "Poker123!", null);
        account.setDeleted(true);
        accounts.saveAndFlush(account);
        error(postWithCsrf("/refresh", cookie(login, "refreshToken")), 401, "AUTH_REFRESH_INVALID");
        error(mvc.perform(get("/api/auth/me").cookie(cookie(login, "accessToken"))).andReturn(), 401, "AUTH_UNAUTHENTICATED");
    }

    @Test
    void jwtRejectsWrongSignatureIssuerAudienceMissingExpiryInvalidSubjectAndRole() throws Exception {
        String subject = account(true).getId().toString();
        for (String variant : List.of("signature", "issuer", "audience", "expiry", "subject", "role")) {
            RSAKey key = variant.equals("signature") ? new RSAKeyGenerator(2048).generate() : signingKey;
            var claims = new JWTClaimsSet.Builder().issuer(variant.equals("issuer") ? "other" : "http://localhost:8090")
                    .audience(variant.equals("audience") ? "other" : "poker")
                    .subject(variant.equals("subject") ? "bad-uuid" : subject).issueTime(Date.from(NOW))
                    .notBeforeTime(Date.from(NOW)).jwtID(UUID.randomUUID().toString())
                    .claim("role", variant.equals("role") ? "SUPERUSER" : "USER");
            if (!variant.equals("expiry")) claims.expirationTime(Date.from(NOW.plusSeconds(900)));
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(key));
            assertThatThrownBy(() -> jwtService.verify(jwt.serialize())).as(variant).isInstanceOf(JwtException.class);
        }
    }

    private Account account(boolean active) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Account account = accounts.saveAndFlush(Account.builder().email("login-" + UUID.randomUUID() + "@example.com")
                    .passwordHash(passwordEncoder.encode("Poker123!"))
                    .accountStatus(active ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION).emailVerified(active).build());
            profiles.saveAndFlush(Profile.builder().account(account).displayName("Khanh").build());
            return account;
        });
    }

    private MvcResult login(String email, String password, Cookie previous) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new LoginRequest(email, password)))
                .cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken);
        if (previous != null) request.cookie(previous);
        return mvc.perform(request).andReturn();
    }

    private MvcResult postWithCsrf(String path, Cookie refresh) throws Exception {
        var request = post("/api/auth" + path).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken);
        if (refresh != null) request.cookie(refresh);
        return mvc.perform(request).andReturn();
    }

    private JsonNode result(MvcResult response) throws Exception {
        return mapper.readTree(response.getResponse().getContentAsString()).get("result");
    }

    private Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as(name).isNotNull();
        return cookie;
    }

    private void error(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("code").asString()).isEqualTo(code);
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary
        MutableClock sessionTestClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        void set(Instant instant) { now.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
