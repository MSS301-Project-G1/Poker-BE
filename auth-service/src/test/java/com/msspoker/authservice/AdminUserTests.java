package com.msspoker.authservice;

import com.msspoker.authservice.dto.admin.request.BanUserRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountBanEventRepository;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AdminUserService;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.session.internal-key=ban-test-key")
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, AdminUserTests.TimeConfiguration.class})
class AdminUserTests {
    private static final String PASSWORD = "Poker123!";
    private static final UUID TAB = UUID.randomUUID();
    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired ProfileRepository profiles;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AccountBanEventRepository events;
    @Autowired AdminUserService adminUsers;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    private MockMvc mvc;
    private Account admin;
    private Account user;
    private SessionTokens userSession;
    private Cookie adminAccess;
    private Cookie csrf;
    private String csrfValue;

    @BeforeEach
    void setup() throws Exception {
        clock.time = NOW;
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").header("X-Tab-Id", TAB)).build();
        admin = account(AccountRole.ADMIN);
        user = account(AccountRole.USER);
        adminAccess = access(login(admin));
        userSession = login(user);
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrf = response.getCookie("XSRF-TOKEN");
        csrfValue = json.readTree(response.getContentAsString()).at("/result/token").asString();
    }

    @Test
    void adminCanSearchAndFilterWithDatabasePaginationAndNoPrivateCredentials() throws Exception {
        String keyword = "admin-search-" + UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            var candidate = account(AccountRole.USER);
            candidate.setEmail(keyword + index + "@example.com");
            candidate.setBanned(index == 1);
            accounts.saveAndFlush(candidate);
            String displayName = keyword + " name " + index;
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var managed = accounts.findById(candidate.getId()).orElseThrow();
                profiles.saveAndFlush(Profile.builder().account(managed).displayName(displayName).build());
            });
        }
        var result = mvc.perform(get("/api/admin/users").cookie(adminAccess).param("keyword", keyword.toUpperCase())
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.content.length()").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(3)).andExpect(jsonPath("$.result.totalPages").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("passwordHash", "refreshToken", "tokenHash");
        mvc.perform(get("/api/admin/users").cookie(adminAccess).param("keyword", keyword).param("banned", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].banned").value(true));
        mvc.perform(get("/api/admin/users").cookie(adminAccess).param("keyword", keyword).param("banned", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(2));
    }

    @Test
    void banBlocksLoginRefreshClaimProtectedAccessAndInternalSocketAuthority() throws Exception {
        mvc.perform(write("/ban", Map.of("reason", "  Repeated abuse  "))
                        .header("X-User-Id", user.getId()).header("X-User-Role", "USER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.banned").value(true))
                .andExpect(jsonPath("$.result.banReason").value("Repeated abuse"))
                .andExpect(jsonPath("$.result.bannedBy").value(admin.getId().toString()));
        assertThat(jwt.verify(userSession.accessToken()).getSubject()).isEqualTo(user.getId().toString());
        for (String path : List.of("/api/auth/me", "/api/auth/session", "/api/auth/ws-token")) {
            mvc.perform(get(path).cookie(access(userSession))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_BANNED"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        for (String path : List.of("/api/auth/refresh", "/api/auth/session/claim")) {
            mvc.perform(post(path).cookie(refresh(userSession), csrf).header("X-XSRF-TOKEN", csrfValue))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_BANNED"));
        }
        mvc.perform(get("/internal/auth/session/check").header("Authorization", "Bearer " + userSession.accessToken())
                        .header("X-Session-Internal-Key", "ban-test-key"))
                .andExpect(status().isForbidden()).andExpect(header().string("X-Session-Error", "AUTH_ACCOUNT_BANNED"));
        assertThatThrownBy(() -> login(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_BANNED));
        assertThatThrownBy(() -> auth.login(new LoginRequest(user.getEmail(), "wrong-password"), TAB))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThat(tokens.findAll().stream().filter(token -> token.getAccountId().equals(user.getId())))
                .allMatch(token -> token.getRevokedAt() != null);
        mvc.perform(get("/api/auth/me").cookie(adminAccess)).andExpect(status().isOk());
    }

    @Test
    void unbanKeepsAuditHistoryAndNeverRevivesOldTokens() throws Exception {
        mvc.perform(write("/ban", Map.of("reason", "Abuse"))).andExpect(status().isOk());
        mvc.perform(write("/ban", Map.of("reason", "Duplicate"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_BAN_ALREADY_ACTIVE"));
        mvc.perform(write("/unban", Map.of("reason", "Appeal accepted"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.banned").value(false));
        mvc.perform(write("/unban", Map.of("reason", "Retry"))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(access(userSession))).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(userSession.refreshToken(), TAB)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_INVALID));
        var history = adminUsers.getBanHistory(admin.getId(), user.getId(), 0, 20);
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(history.content()).allMatch(event -> event.actorId().equals(admin.getId()));
        assertThat(history.content()).extracting(event -> event.reason()).containsExactlyInAnyOrder("Abuse", "Appeal accepted");
        assertThat(login(user)).isNotNull();
    }

    @Test
    void timedBanExpiresAtTheExactDeadlineWithoutReactivatingTheOldSession() throws Exception {
        Instant until = NOW.plusSeconds(10);
        mvc.perform(write("/ban", Map.of("reason", "Temporary ban", "until", until.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.bannedUntil").value(until.toString()));
        clock.time = until.minusNanos(1);
        assertThat(auth.restrictions(user.getId()).banned()).isTrue();
        clock.time = until;
        assertThat(auth.restrictions(user.getId()).banned()).isFalse();
        assertThat(adminUsers.getUser(admin.getId(), user.getId()).isBanned()).isFalse();
        var filtered = adminUsers.getUsers(admin.getId(), user.getEmail(), false, 0, 20);
        assertThat(filtered.totalElements()).isEqualTo(1);
        mvc.perform(get("/api/auth/me").cookie(access(userSession))).andExpect(status().isUnauthorized());
        assertThat(login(user)).isNotNull();
        assertThat(adminUsers.getBanHistory(admin.getId(), user.getId(), 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    void normalUsersAnonymousAndStaleAdminRolesCannotManageAccounts() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").cookie(access(userSession)).header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/" + user.getId() + "/ban").cookie(access(userSession), csrf)
                        .header("X-XSRF-TOKEN", csrfValue).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Forged admin\"}"))
                .andExpect(status().isForbidden());
        admin.setRole(AccountRole.USER);
        accounts.saveAndFlush(admin);
        mvc.perform(get("/api/admin/users").cookie(adminAccess)).andExpect(status().isForbidden());
        assertThatThrownBy(() -> adminUsers.banUser(user.getId(), admin.getId(), new BanUserRequest("No access", null)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
        assertThat(accounts.findById(user.getId()).orElseThrow().isBanned()).isFalse();
    }

    @Test
    void administratorsCannotBanOrUnbanThemselvesOrAnotherAdministrator() throws Exception {
        Account otherAdmin = account(AccountRole.ADMIN);
        for (UUID target : List.of(admin.getId(), otherAdmin.getId())) {
            for (String action : List.of("ban", "unban")) {
                mvc.perform(post("/api/admin/users/" + target + "/" + action).cookie(adminAccess, csrf)
                                .header("X-XSRF-TOKEN", csrfValue).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Forbidden\"}"))
                        .andExpect(status().isForbidden());
            }
        }
        assertThat(events.findByAccountId(otherAdmin.getId(), org.springframework.data.domain.PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void writesRequireCsrfAndValidReasonsAndFutureDeadlines() throws Exception {
        mvc.perform(post("/api/admin/users/" + user.getId() + "/ban").cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing csrf\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        for (Map<String, String> body : List.of(Map.<String, String>of(), Map.of("reason", "   "),
                Map.of("reason", "x".repeat(501)), Map.of("reason", "Invalid date", "until", NOW.toString()),
                Map.of("reason", "Past date", "until", NOW.minusSeconds(1).toString()),
                Map.of("reason", "Bad date", "until", "not-a-date"))) {
            mvc.perform(write("/ban", body)).andExpect(status().isBadRequest());
        }
        mvc.perform(write("/unban", Map.of())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").cookie(adminAccess).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").cookie(adminAccess).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").cookie(adminAccess).param("keyword", "x".repeat(101))).andExpect(status().isBadRequest());
        assertThat(adminUsers.getBanHistory(admin.getId(), user.getId(), 0, 20).totalElements()).isZero();
        assertThat(accounts.findById(user.getId()).orElseThrow().isBanned()).isFalse();
    }

    @Test
    void restrictionsUseTheRawThreeFieldContractAndDeletedAccountsAreNotUsable() throws Exception {
        var before = mvc.perform(get("/internal/accounts/" + user.getId() + "/restrictions"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.banned").value(false)).andExpect(jsonPath("$.result").doesNotExist()).andReturn();
        assertThat(json.readTree(before.getResponse().getContentAsString()).size()).isEqualTo(3);
        mvc.perform(write("/ban", Map.of("reason", "Internal restrictions"))).andExpect(status().isOk());
        mvc.perform(get("/internal/accounts/" + user.getId() + "/restrictions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.banned").value(true));
        mvc.perform(get("/internal/accounts/" + UUID.randomUUID() + "/restrictions")).andExpect(status().isNotFound());
        var deleted = accounts.findById(user.getId()).orElseThrow();
        deleted.setDeleted(true);
        accounts.saveAndFlush(deleted);
        mvc.perform(get("/internal/accounts/" + user.getId() + "/restrictions")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users/" + user.getId()).cookie(adminAccess)).andExpect(status().isNotFound());
        mvc.perform(write("/ban", Map.of("reason", "Deleted user"))).andExpect(status().isNotFound());
    }

    @Test
    void adminIdsListUndeletedAdminsAsARawArray() throws Exception {
        String body = mvc.perform(get("/internal/accounts/admins"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$").isArray()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains(admin.getId().toString()).doesNotContain(user.getId().toString());
        var deleted = accounts.findById(admin.getId()).orElseThrow();
        deleted.setDeleted(true);
        accounts.saveAndFlush(deleted);
        assertThat(auth.adminIds()).doesNotContain(admin.getId());
    }

    @Test
    void aConcurrentLoginCannotLeaveUsableTokensAfterBanCommits() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                try { auth.login(new LoginRequest(user.getEmail(), PASSWORD), TAB); }
                catch (ApiException error) { assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_BANNED); }
                return null;
            });
            var ban = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                return adminUsers.banUser(admin.getId(), user.getId(), new BanUserRequest("Concurrent ban", null));
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            login.get(10, TimeUnit.SECONDS);
            assertThat(ban.get(10, TimeUnit.SECONDS).isBanned()).isTrue();
        }
        assertThat(tokens.findAll().stream().filter(token -> token.getAccountId().equals(user.getId())))
                .allMatch(token -> token.getRevokedAt() != null);
        assertThatThrownBy(() -> login(user)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_BANNED));
    }

    private MockHttpServletRequestBuilder write(String action, Object body) {
        return post("/api/admin/users/" + user.getId() + action).cookie(adminAccess, csrf)
                .header("X-XSRF-TOKEN", csrfValue).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private Account account(AccountRole role) {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD)).accountStatus(AccountStatus.ACTIVE).emailVerified(true).role(role).build());
    }

    private SessionTokens login(Account account) { return auth.login(new LoginRequest(account.getEmail(), PASSWORD), TAB); }
    private Cookie access(SessionTokens session) { return new Cookie("accessToken", session.accessToken()); }
    private Cookie refresh(SessionTokens session) { return new Cookie("refreshToken", session.refreshToken()); }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock banTestClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private volatile Instant time = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
}
