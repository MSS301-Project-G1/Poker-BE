package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.CheckinRewardStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.DailyCheckinRepository;
import com.msspoker.authservice.service.CheckinService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, CheckinTests.TimeConfiguration.class})
class CheckinTests {
    private static final Instant NOW = Instant.parse("2026-10-31T16:59:59Z");
    private static final WalletFixture WALLET = new WalletFixture();
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private DailyCheckinRepository checkins;
    @Autowired private CheckinService service;
    @Autowired private JwtService jwt;
    @Autowired private JsonMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    private MockMvc mvc;
    private Account caller;
    private Cookie access;
    private Cookie csrfCookie;
    private String csrf;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("auth.wallet.base-url", WALLET::url);
        registry.add("auth.wallet.request-timeout", () -> "500ms");
    }

    @BeforeEach
    void setup() throws Exception {
        checkins.deleteAllInBatch();
        WALLET.mode.set("SUCCESS");
        WALLET.ledger.clear();
        WALLET.requests.set(0);
        WALLET.last.set(null);
        WALLET.onCredit.set(null);
        clock.now.set(NOW);
        caller = account();
        access = new Cookie("accessToken", jwt.issue(caller, NOW.plusSeconds(3600)));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrfCookie = response.getCookie("XSRF-TOKEN");
        csrf = json.readTree(response.getContentAsString()).get("result").get("token").asString();
    }

    @AfterAll
    static void closeFixture() { WALLET.close(); }

    @Test
    void commitsEntitlementBeforeWalletCreditAndReturnsOnlyTheOwnersCalendar() throws Exception {
        WALLET.onCredit.set(command -> assertThat(jdbc.queryForObject(
                "SELECT reward_status FROM daily_checkins WHERE id = ?", String.class,
                UUID.fromString(command.get("refId").asString()))).isEqualTo("PENDING"));
        var result = performCheckin(201);
        assertThat(result.get("rewardCoins").asLong()).isEqualTo(50);
        assertThat(result.get("date").asString()).isEqualTo("2026-10-31");
        assertThat(result.get("rewardStatus").asString()).isEqualTo("REWARDED");
        assertThat(result.get("isMakeup").asBoolean()).isFalse();
        var command = WALLET.last.get();
        assertThat(command.get("accountId").asString()).isEqualTo(caller.getId().toString());
        assertThat(command.get("idempotencyKey").asString()).isEqualTo("checkin:2026-10-31:" + caller.getId());
        assertThat(command.get("reason").asString()).isEqualTo("DAILY_CHECKIN");
        assertThat(command.get("currencyType").asString()).isEqualTo("COIN");
        assertThat(WALLET.ledger.size()).isEqualTo(1);
        mvc.perform(post("/api/checkin").cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_CHECKIN_ALREADY_EXISTS"));
        assertThat(WALLET.requests.get()).isEqualTo(1);
        mvc.perform(get("/api/checkin/calendar").cookie(access).param("month", "2026-10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.days.length()").value(1))
                .andExpect(jsonPath("$.result.checkedInToday").value(true))
                .andExpect(jsonPath("$.result.days[0].rewardStatus").value("REWARDED"));
    }

    @Test
    void concurrentCheckinsCreateOnlyOneEntitlementAndOneCredit() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                try { service.checkIn(caller.getId()); return "success"; }
                catch (ApiException exception) { return exception.getErrorCode().name(); }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "AUTH_CHECKIN_ALREADY_EXISTS");
        }
        assertThat(checkins.count()).isEqualTo(1);
        assertThat(WALLET.requests.get()).isEqualTo(1);
        assertThat(WALLET.ledger.size()).isEqualTo(1);
    }

    @Test
    void lostResponseAfterWalletCreditRetriesWithTheSameKeyAndNeverAddsAnotherReward() throws Exception {
        WALLET.mode.set("LOST_RESPONSE");
        var result = performCheckin(201);
        assertThat(result.get("rewardStatus").asString()).isEqualTo("PENDING");
        assertThat(WALLET.ledger.size()).isEqualTo(1); // Wallet committed before the response timed out.
        String firstKey = WALLET.last.get().get("idempotencyKey").asString();
        int attempts = WALLET.requests.get();
        service.retryPendingRewards();
        assertThat(WALLET.requests.get()).isEqualTo(attempts); // Backoff prevents immediate resends.
        WALLET.mode.set("SUCCESS");
        clock.now.set(NOW.plusSeconds(30));
        service.retryPendingRewards();
        assertThat(WALLET.last.get().get("idempotencyKey").asString()).isEqualTo(firstKey);
        var saved = checkins.findAll().getFirst();
        assertThat(saved.getRewardStatus()).isEqualTo(CheckinRewardStatus.REWARDED);
        assertThat(saved.getDeliveryAttempts()).isEqualTo(2);
        assertThat(WALLET.ledger.size()).isEqualTo(1);
        assertThat(WALLET.ledger.values().stream().mapToLong(row -> ((Number) row.get("amount")).longValue()).sum()).isEqualTo(50);
        service.retryPendingRewards();
        assertThat(WALLET.requests.get()).isEqualTo(attempts + 1);
    }

    @Test
    void unavailableOrMismatchedWalletResponsesStayPendingUntilTheCreditIsConfirmed() throws Exception {
        WALLET.mode.set("ERROR");
        performCheckin(201);
        assertThat(WALLET.ledger).isEmpty();
        WALLET.mode.set("MISMATCH");
        clock.now.set(NOW.plusSeconds(30));
        service.retryPendingRewards();
        assertThat(checkins.findAll().getFirst().getRewardStatus()).isEqualTo(CheckinRewardStatus.PENDING);
        assertThat(checkins.findAll().getFirst().getWalletTransactionId()).isNull();
        WALLET.mode.set("SUCCESS");
        clock.now.set(NOW.plusSeconds(60));
        service.retryPendingRewards();
        assertThat(checkins.findAll().getFirst().getRewardStatus()).isEqualTo(CheckinRewardStatus.REWARDED);
        assertThat(WALLET.ledger.size()).isEqualTo(1);
    }

    @Test
    void usesVietnamMidnightAcrossMonthBoundaryAndSeparatesAccountsAndMonths() throws Exception {
        performCheckin(201);
        clock.now.set(NOW.plusSeconds(2)); // UTC is still October 31; Vietnam is now November 1.
        performCheckin(201);
        service.checkIn(account().getId());
        for (String month : List.of("2026-10", "2026-11")) {
            mvc.perform(get("/api/checkin/calendar").cookie(access).param("month", month))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.result.days.length()").value(1))
                    .andExpect(jsonPath("$.result.today").value("2026-11-01"))
                    .andExpect(jsonPath("$.result.checkedInToday").value(true));
        }
        assertThat(checkins.findByAccountIdAndDateBetweenOrderByDateAsc(caller.getId(),
                java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-11-30"))).hasSize(2);
        for (String bad : List.of("2026-13", "2026-1", "0000-01", "bad")) {
            mvc.perform(get("/api/checkin/calendar").cookie(access).param("month", bad)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void requiresAccessCsrfAndActiveAccountAndDoesNotTrustClientIdentity() throws Exception {
        mvc.perform(get("/api/checkin/calendar").param("month", "2026-10")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/checkin").cookie(csrfCookie).header("X-XSRF-TOKEN", csrf)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/checkin").cookie(access)).andExpect(status().isForbidden());
        mvc.perform(post("/api/checkin").cookie(access, csrfCookie).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        assertThat(checkins.count()).isZero();
        Account other = account();
        mvc.perform(post("/api/checkin").cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrf)
                        .header("X-User-Id", other.getId().toString())).andExpect(status().isCreated());
        assertThat(checkins.findAll().getFirst().getAccountId()).isEqualTo(caller.getId());
        caller.setDeleted(true);
        accounts.saveAndFlush(caller);
        mvc.perform(get("/api/checkin/calendar").cookie(access).param("month", "2026-10"))
                .andExpect(status().isUnauthorized());
        assertThat(WALLET.requests.get()).isEqualTo(1);
    }

    private JsonNode performCheckin(int status) throws Exception {
        MvcResult response = mvc.perform(post("/api/checkin").cookie(access, csrfCookie)
                        .header("X-XSRF-TOKEN", csrf)).andExpect(status().is(status)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("result");
    }

    private Account account() {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("unused").emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock checkinTestClock() { return new MutableClock(); }
    }

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    // Implements wallet's HTTP envelope/idempotency contract; this is not the real wallet app.
    static final class WalletFixture implements AutoCloseable {
        private static final JsonMapper JSON = JsonMapper.builder().build();
        final AtomicReference<String> mode = new AtomicReference<>("SUCCESS");
        final AtomicReference<JsonNode> last = new AtomicReference<>();
        final AtomicReference<java.util.function.Consumer<JsonNode>> onCredit = new AtomicReference<>();
        final AtomicInteger requests = new AtomicInteger();
        final Map<String, Map<String, Object>> ledger = new ConcurrentHashMap<>();
        final java.util.concurrent.ExecutorService executor = Executors.newCachedThreadPool();
        final HttpServer server;
        WalletFixture() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.setExecutor(executor);
                server.createContext("/internal/wallet/credit", this::credit);
                server.start();
            } catch (IOException exception) { throw new IllegalStateException(exception); }
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        void credit(HttpExchange exchange) throws IOException {
            try (exchange) {
                assertThat(exchange.getRequestMethod()).isEqualTo("POST");
                JsonNode command = JSON.readTree(exchange.getRequestBody().readAllBytes());
                last.set(command);
                requests.incrementAndGet();
                var callback = onCredit.get();
                if (callback != null) callback.accept(command);
                String selected = mode.get();
                if (selected.equals("ERROR")) { exchange.sendResponseHeaders(503, -1); return; }
                Map<String, Object> row = ledger.computeIfAbsent(command.get("idempotencyKey").asString(), key -> Map.of(
                        "id", UUID.randomUUID().toString(), "accountId", command.get("accountId").asString(),
                        "amount", command.get("amount").asLong(), "reason", command.get("reason").asString(),
                        "refId", command.get("refId").asString(), "idempotencyKey", key,
                        "currencyType", command.get("currencyType").asString(), "balanceAfter", 50));
                if (selected.equals("LOST_RESPONSE")) {
                    try { Thread.sleep(1500); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                    return;
                }
                if (selected.equals("MISMATCH")) {
                    row = new java.util.HashMap<>(row);
                    row.put("accountId", UUID.randomUUID().toString());
                }
                byte[] bytes = JSON.writeValueAsBytes(Map.of("code", "WALLET_SUCCESS", "data", row));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
