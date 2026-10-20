package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.ForgotPasswordRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.request.ResetPasswordRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.PasswordResetOtpRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class PasswordResetTests {
    private static final String OLD_PASSWORD = "OldPoker123!";
    private static final String NEW_PASSWORD = "NewPoker123!";
    private static final String PATH = "/api/auth/password";
    @Autowired private WebApplicationContext context;
    @Autowired private AuthService auth;
    @Autowired private ProfileRepository profiles;
    @Autowired private ProfileService profileService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private AccountRepository accounts;
    @Autowired private PasswordResetOtpRepository otps;
    @Autowired private RegistrationOtpRepository registrationOtps;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Autowired private JsonMapper json;
    @MockitoBean private JavaMailSender mail;
    @MockitoBean private Clock clock;
    private MockMvc mvc;
    private Instant now;

    @BeforeEach
    void setup() {
        reset(mail, clock);
        now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        when(clock.instant()).thenReturn(now);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void publicFlowConsumesOtpRevokesEveryDeviceAndRequiresLoginWithNewPassword() throws Exception {
        Account account = account(true, false);
        var first = auth.login(new LoginRequest(account.getEmail(), OLD_PASSWORD), null);
        var second = auth.login(new LoginRequest(account.getEmail(), OLD_PASSWORD), null);
        Account other = account(true, false);
        var unrelated = auth.login(new LoginRequest(other.getEmail(), OLD_PASSWORD), null);
        var forgot = postJson("/forgot", new ForgotPasswordRequest(account.getEmail().toUpperCase()));
        assertThat(forgot.getResponse().getStatus()).isEqualTo(200);
        String code = code(1);
        var saved = otps.findById(account.getId()).orElseThrow();
        assertThat(saved.getCodeHash()).isNotEqualTo(code);
        assertThat(encoder.matches(code, saved.getCodeHash())).isTrue();
        assertThat(saved.getExpiresAt()).isEqualTo(now.plusSeconds(300));
        var response = postJson("/reset", request(account, code));
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getCookies()).hasSize(2).allSatisfy(cookie -> {
            assertThat(cookie.getMaxAge()).isZero();
            assertThat(cookie.getValue()).isEmpty();
        });
        assertThat(result(response).has("result")).isFalse();
        assertThat(otps.findById(account.getId())).isEmpty();
        assertThat(tokens.findAll().stream().filter(t -> t.getAccountId().equals(account.getId())))
                .hasSize(2).allSatisfy(t -> assertThat(t.getRevokedAt()).isEqualTo(now));
        assertError(() -> auth.refresh(first.refreshToken()), ErrorCode.AUTH_REFRESH_INVALID);
        assertError(() -> auth.refresh(second.refreshToken()), ErrorCode.AUTH_REFRESH_INVALID);
        assertError(() -> auth.login(new LoginRequest(account.getEmail(), OLD_PASSWORD), null), ErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThat(auth.login(new LoginRequest(account.getEmail(), NEW_PASSWORD), null)).isNotNull();
        assertThat(auth.refresh(unrelated.refreshToken())).isNotNull();
        assertError(() -> auth.resetPassword(request(account, code)), ErrorCode.AUTH_OTP_INVALID);
        // This phase revokes refresh credentials, not already-issued access JWTs.
        assertThat(jwtService.verify(first.accessToken()).getSubject()).isEqualTo(account.getId().toString());
    }

    @Test
    void forgotHasOnePublicResponseForMissingIneligibleAndCoolingDownAccounts() throws Exception {
        Account pending = account(false, false);
        Account deleted = account(true, true);
        for (String email : List.of(UUID.randomUUID() + "@example.com", pending.getEmail(), deleted.getEmail())) {
            var response = postJson("/forgot", new ForgotPasswordRequest(email));
            assertThat(response.getResponse().getStatus()).isEqualTo(200);
            assertThat(result(response).get("code").asString()).isEqualTo("success_request");
            assertThat(result(response).has("result")).isFalse();
        }
        verifyNoInteractions(mail);
        Account active = account(true, false);
        var first = postJson("/forgot", new ForgotPasswordRequest(active.getEmail()));
        String hash = otps.findById(active.getId()).orElseThrow().getCodeHash();
        var cooling = postJson("/forgot", new ForgotPasswordRequest(active.getEmail()));
        var missing = postJson("/forgot", new ForgotPasswordRequest(UUID.randomUUID() + "@example.com"));
        assertThat(result(first).get("message")).isEqualTo(result(cooling).get("message")).isEqualTo(result(missing).get("message"));
        assertThat(cooling.getResponse().getStatus()).isEqualTo(200);
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        assertThat(otps.findById(active.getId()).orElseThrow().getCodeHash()).isEqualTo(hash);
    }

    @Test
    void fiveWrongAttemptsPersistAndResendAtCooldownBoundaryReplacesTheCode() throws Exception {
        Account account = account(true, false);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String oldCode = code(1);
        String wrong = oldCode.equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertError(() -> auth.resetPassword(request(account, wrong)), attempt == 5
                    ? ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED : ErrorCode.AUTH_OTP_INVALID);
            assertThat(otps.findById(account.getId()).orElseThrow().getFailedAttempts()).isEqualTo(attempt);
        }
        assertError(() -> auth.resetPassword(request(account, oldCode)), ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String replacement = code(2);
        assertThat(replacement).isNotEqualTo(oldCode);
        assertThat(otps.findById(account.getId()).orElseThrow().getFailedAttempts()).isZero();
        assertError(() -> auth.resetPassword(request(account, oldCode)), ErrorCode.AUTH_OTP_INVALID);
        auth.resetPassword(request(account, replacement));
    }

    @Test
    void exactExpiryAndRegistrationOtpCannotAuthorizeAReset() {
        Account account = account(true, false);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String code = code(1);
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        assertError(() -> auth.resetPassword(request(account, code)), ErrorCode.AUTH_OTP_EXPIRED);
        assertThat(encoder.matches(OLD_PASSWORD, accounts.findById(account.getId()).orElseThrow().getPasswordHash())).isTrue();
        Account pending = account(false, false);
        registrationOtps.saveAndFlush(RegistrationOtp.builder().account(pending).codeHash(encoder.encode("012345"))
                .lastSentAt(now).expiresAt(now.plusSeconds(600)).build());
        assertError(() -> auth.resetPassword(request(pending, "012345")), ErrorCode.AUTH_OTP_INVALID);
        assertThat(accounts.findById(pending.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void passwordValidationDoesNotConsumeOtpOrChangePassword() throws Exception {
        Account account = account(true, false);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String code = code(1);
        for (String password : List.of("short", "😀".repeat(7), "😀".repeat(19))) {
            var response = postJson("/reset", new ResetPasswordRequest(account.getEmail(), code, password, password));
            assertThat(response.getResponse().getStatus()).isEqualTo(400);
        }
        var mismatch = postJson("/reset", new ResetPasswordRequest(account.getEmail(), code, NEW_PASSWORD, "different"));
        assertThat(result(mismatch).get("code").asString()).isEqualTo("AUTH_PASSWORD_MISMATCH");
        assertThat(otps.findById(account.getId()).orElseThrow().getFailedAttempts()).isZero();
        assertThat(encoder.matches(OLD_PASSWORD, accounts.findById(account.getId()).orElseThrow().getPasswordHash())).isTrue();
        auth.resetPassword(request(account, code));
    }

    @Test
    void smtpFailureKeepsGenericResponseAndPreservesPreviousCodeAndAttempts() throws Exception {
        Account account = account(true, false);
        doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(postJson("/forgot", new ForgotPasswordRequest(account.getEmail())).getResponse().getStatus()).isEqualTo(200);
        assertThat(otps.findById(account.getId())).isEmpty();
        reset(mail);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String oldCode = code(1);
        String hash = otps.findById(account.getId()).orElseThrow().getCodeHash();
        String wrong = oldCode.equals("000000") ? "000001" : "000000";
        assertError(() -> auth.resetPassword(request(account, wrong)), ErrorCode.AUTH_OTP_INVALID);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(postJson("/forgot", new ForgotPasswordRequest(account.getEmail())).getResponse().getStatus()).isEqualTo(200);
        var previous = otps.findById(account.getId()).orElseThrow();
        assertThat(previous.getCodeHash()).isEqualTo(hash);
        assertThat(previous.getFailedAttempts()).isEqualTo(1);
        auth.resetPassword(request(account, oldCode));
    }

    @Test
    void concurrentRequestsCanConsumeTheSameOtpOnlyOnce() throws Exception {
        Account account = account(true, false);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String code = code(1);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                try {
                    auth.resetPassword(request(account, code));
                    return "success";
                } catch (ApiException exception) { return exception.getErrorCode().name(); }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "AUTH_OTP_INVALID");
        }
    }

    @Test
    void resetSerializesConcurrentOldLoginRefreshAndProfileSaveWithoutRestoringOldPassword() throws Exception {
        Account account = account(true, false);
        new TransactionTemplate(transactions).executeWithoutResult(status -> profiles.saveAndFlush(Profile.builder()
                .account(accounts.findById(account.getId()).orElseThrow()).displayName("Before reset").build()));
        var session = auth.login(new LoginRequest(account.getEmail(), OLD_PASSWORD), null);
        auth.forgotPassword(new ForgotPasswordRequest(account.getEmail()));
        String code = code(1);
        var started = new CountDownLatch(3);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                auth.resetPassword(request(account, code));
                futures.add(executor.submit(() -> {
                    started.countDown();
                    try {
                        auth.login(new LoginRequest(account.getEmail(), OLD_PASSWORD), null);
                        return "unexpected login";
                    } catch (ApiException exception) { return exception.getErrorCode().name(); }
                }));
                futures.add(executor.submit(() -> {
                    started.countDown();
                    try {
                        auth.refresh(session.refreshToken());
                        return "unexpected refresh";
                    } catch (ApiException exception) { return exception.getErrorCode().name(); }
                }));
                futures.add(executor.submit(() -> {
                    started.countDown();
                    profileService.updateMyProfile(account.getId(), new UpdateProfileRequest(
                            "After reset", null, "0901234567", null, null, null, null, null));
                    return "profile saved";
                }));
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    // Ensure all three operations reach PostgreSQL before the reset commits.
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    long waiting;
                    do {
                        jdbc.execute("SELECT pg_stat_clear_snapshot()");
                        waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                                + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Long.class);
                        if (waiting >= 3) break;
                        Thread.sleep(25);
                    } while (System.nanoTime() < deadline);
                    assertThat(waiting).isGreaterThanOrEqualTo(3);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            });
            assertThat(futures.get(0).get(10, TimeUnit.SECONDS)).isEqualTo("AUTH_INVALID_CREDENTIALS");
            assertThat(futures.get(1).get(10, TimeUnit.SECONDS)).isEqualTo("AUTH_REFRESH_INVALID");
            assertThat(futures.get(2).get(10, TimeUnit.SECONDS)).isEqualTo("profile saved");
        }
        var saved = accounts.findById(account.getId()).orElseThrow();
        assertThat(encoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(profiles.findById(account.getId()).orElseThrow().getDisplayName()).isEqualTo("After reset");
        assertThat(otps.findById(account.getId())).isEmpty();
    }

    @Test
    void recoveryEndpointsValidateInputAndAppearInSwaggerWithoutCookieOrCsrfRequirements() throws Exception {
        mvc.perform(post(PATH + "/forgot").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/reset").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var docs = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (String path : List.of(PATH + "/forgot", PATH + "/reset")) {
            var operation = docs.get("paths").get(path).get("post");
            assertThat(operation.get("requestBody").get("required").asBoolean()).isTrue();
            assertThat(operation.path("security").isEmpty()).isTrue();
        }
    }

    private Account account(boolean verified, boolean deleted) {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode(OLD_PASSWORD)).emailVerified(verified).deleted(deleted)
                .accountStatus(verified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION).build());
    }

    private ResetPasswordRequest request(Account account, String code) {
        return new ResetPasswordRequest(account.getEmail(), code, NEW_PASSWORD, NEW_PASSWORD);
    }

    private MvcResult postJson(String path, Object request) throws Exception {
        return mvc.perform(post(PATH + path).cookie(new Cookie("accessToken", "expired-or-invalid"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andReturn();
    }

    private JsonNode result(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String code(int sends) {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(sends)).send(captor.capture());
        var message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Poker - Đặt lại mật khẩu");
        var matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private void assertError(Runnable operation, ErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
