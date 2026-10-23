package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.ChangePasswordRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.PasswordResetOtp;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.PasswordResetOtpRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class ChangePasswordTests {
    private static final String PATH = "/api/auth/password/change";
    private static final String OLD = "OldPoker123!";
    private static final String NEW = "NewPoker123!";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordResetOtpRepository resetOtps;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private AuthService auth;
    @Autowired private JwtService jwtService;
    @Autowired private JsonMapper json;
    private MockMvc mvc;
    private Account caller;
    private Cookie access;
    private Cookie csrfCookie;
    private String csrf;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        caller = account();
        access = access(caller);
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrfCookie = response.getCookie("XSRF-TOKEN");
        csrf = json.readTree(response.getContentAsString()).get("result").get("token").asString();
    }

    @Test
    void changesPasswordRevokesAllOwnDevicesClearsCookiesAndInvalidatesPendingRecoveryOtp() throws Exception {
        var first = auth.login(new LoginRequest(caller.getEmail(), OLD), null);
        var second = auth.login(new LoginRequest(caller.getEmail(), OLD), null);
        var other = auth.login(new LoginRequest(account().getEmail(), OLD), null);
        pendingOtp();
        mvc.perform(change(OLD, NEW, NEW).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success_request"))
                .andExpect(jsonPath("$.path").value(PATH)).andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(cookie().maxAge("accessToken", 0)).andExpect(cookie().maxAge("refreshToken", 0));
        assertThat(encoder.matches(NEW, accounts.findById(caller.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(resetOtps.findById(caller.getId())).isEmpty();
        assertThat(refreshTokens.findAll().stream().filter(t -> t.getAccountId().equals(caller.getId())))
                .hasSize(2).allSatisfy(t -> assertThat(t.getRevokedAt()).isNotNull());
        for (String raw : List.of(first.refreshToken(), second.refreshToken())) {
            assertThatThrownBy(() -> auth.refresh(raw)).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getErrorCode().name()).isEqualTo("AUTH_REFRESH_INVALID"));
        }
        assertThatThrownBy(() -> auth.login(new LoginRequest(caller.getEmail(), OLD), null)).isInstanceOf(ApiException.class);
        assertThat(auth.login(new LoginRequest(caller.getEmail(), NEW), null)).isNotNull();
        assertThat(auth.refresh(other.refreshToken())).isNotNull();
        // Revoking refresh credentials does not blacklist already-issued access JWTs.
        assertThat(jwtService.verify(first.accessToken()).getSubject()).isEqualTo(caller.getId().toString());
    }

    @Test
    void wrongCurrentPasswordDoesNotChangePasswordOtpOrSessions() throws Exception {
        var session = auth.login(new LoginRequest(caller.getEmail(), OLD), null);
        pendingOtp();
        String hash = accounts.findById(caller.getId()).orElseThrow().getPasswordHash();
        for (String wrong : List.of("wrong-password", "😀".repeat(19))) {
            mvc.perform(change(wrong, NEW, NEW).cookie(access)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_CURRENT_PASSWORD_INVALID"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(accounts.findById(caller.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(resetOtps.findById(caller.getId())).isPresent();
        assertThat(auth.refresh(session.refreshToken())).isNotNull();
    }

    @Test
    void validatesConfirmationAndUnicodeByteLimitsWithoutChangingState() throws Exception {
        var session = auth.login(new LoginRequest(caller.getEmail(), OLD), null);
        String hash = accounts.findById(caller.getId()).orElseThrow().getPasswordHash();
        mvc.perform(change(OLD, NEW, "different").cookie(access)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_MISMATCH"));
        for (String invalid : List.of("short", "😀".repeat(7), "😀".repeat(19))) {
            mvc.perform(change(OLD, invalid, invalid).cookie(access)).andExpect(status().isBadRequest())
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        mvc.perform(post(PATH).cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(caller.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(auth.refresh(session.refreshToken())).isNotNull();
    }

    @Test
    void requiresAccessAndCsrfAndTakesIdentityOnlyFromJwt() throws Exception {
        Account other = account();
        mvc.perform(change(OLD, NEW, NEW)).andExpect(status().isUnauthorized());
        mvc.perform(change(OLD, NEW, NEW).cookie(new Cookie("accessToken", "forged")))
                .andExpect(status().isUnauthorized());
        String body = json.writeValueAsString(new ChangePasswordRequest(OLD, NEW, NEW));
        mvc.perform(post(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(post(PATH).cookie(access, csrfCookie).header("X-XSRF-TOKEN", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(change(OLD, NEW, NEW).cookie(access).header("X-User-Id", other.getId().toString()))
                .andExpect(status().isOk());
        assertThat(encoder.matches(OLD, accounts.findById(other.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(encoder.matches(NEW, accounts.findById(caller.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void rejectsUnavailableAccountsEvenWithSignedAccessCookies() throws Exception {
        caller.setEmailVerified(false);
        caller.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        accounts.saveAndFlush(caller);
        mvc.perform(change(OLD, NEW, NEW).cookie(access)).andExpect(status().isConflict());
        caller.setEmailVerified(true);
        caller.setAccountStatus(AccountStatus.ACTIVE);
        caller.setDeleted(true);
        accounts.saveAndFlush(caller);
        mvc.perform(change(OLD, NEW, NEW).cookie(access)).andExpect(status().isUnauthorized());
        assertThat(encoder.matches(OLD, accounts.findById(caller.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void concurrentChangesUsingTheOldPasswordCanSucceedOnlyOnce() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                try {
                    auth.changePassword(caller.getId(), new ChangePasswordRequest(OLD, NEW, NEW));
                    return "success";
                } catch (ApiException exception) { return exception.getErrorCode().name(); }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "AUTH_CURRENT_PASSWORD_INVALID");
        }
    }

    @Test
    void swaggerShowsPasswordFieldsCsrfAndNoClientSuppliedIdentity() throws Exception {
        var document = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var operation = document.get("paths").get(PATH).get("post");
        assertThat(operation.get("security").toString()).contains("csrfToken");
        assertThat(operation.path("parameters").isEmpty()).isTrue();
        var properties = document.get("components").get("schemas").get("ChangePasswordRequest").get("properties");
        assertThat(properties.size()).isEqualTo(3);
        for (String field : List.of("currentPassword", "newPassword", "confirmPassword")) {
            assertThat(properties.get(field).get("writeOnly").asBoolean()).isTrue();
        }
    }

    private Account account() {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode(OLD)).emailVerified(true).accountStatus(AccountStatus.ACTIVE).build());
    }

    private Cookie access(Account account) {
        return new Cookie("accessToken", jwtService.issue(account, Instant.now().plusSeconds(900)));
    }

    private void pendingOtp() {
        resetOtps.saveAndFlush(PasswordResetOtp.builder().account(caller).codeHash(encoder.encode("012345"))
                .lastSentAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
    }

    private MockHttpServletRequestBuilder change(String current, String password, String confirmation) throws Exception {
        return post(PATH).cookie(csrfCookie).header("X-XSRF-TOKEN", csrf).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ChangePasswordRequest(current, password, confirmation)));
    }
}
