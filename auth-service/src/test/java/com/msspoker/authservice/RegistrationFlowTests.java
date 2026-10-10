package com.msspoker.authservice;

import com.msspoker.authservice.controller.RegistrationController;
import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.RegistrationService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfiguration.class, RegistrationInfrastructureConfiguration.class,
        RegistrationFlowTests.TimeConfiguration.class})
class RegistrationFlowTests {
    @Autowired private RegistrationController controller;
    @Autowired private GlobalExceptionHandler exceptionHandler;
    @Autowired private RegistrationService service;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private RegistrationOtpRepository otps;
    @Autowired private RegistrationEventRepository events;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private GenericContainer<?> mailpitContainer;
    @Autowired private MutableClock clock;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2026-10-10T01:00:00Z"));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(exceptionHandler).build();
    }

    @Test
    void registerMailVerifyFlowCreatesSharedProfileAndExactlyOneEventWithoutLoginCookies() throws Exception {
        String email = email();
        MvcResult registration = request("/register", new RegisterRequest(email.toUpperCase(),
                "test-password", "test-password", "  Khanh  "));
        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
        JsonNode response = jsonMapper.readTree(registration.getResponse().getContentAsString());
        UUID accountId = UUID.fromString(response.get("accountId").asString());
        assertThat(accountId.version()).isEqualTo(7);
        assertThat(response.get("email").asString()).isEqualTo(email);
        assertThat(response.get("otpExpiresAt").asString()).isEqualTo("2026-10-10T01:05:00Z");
        assertThat(response.get("resendAvailableAt").asString()).isEqualTo("2026-10-10T01:01:00Z");
        assertThat(profiles.findById(accountId).orElseThrow().getDisplayName()).isEqualTo("Khanh");
        assertThat(accounts.findById(accountId).orElseThrow().isEmailVerified()).isFalse();
        String code = readCode(email);
        assertThat(code).matches("[0-9]{6}");
        String stored = otps.findById(accountId).orElseThrow().getCodeHash();
        assertThat(stored).isNotEqualTo(code);
        assertThat(passwordEncoder.matches(code, stored)).isTrue();
        assertThat(passwordEncoder.matches("test-password", accounts.findById(accountId).orElseThrow().getPasswordHash()))
                .isTrue();

        MvcResult verification = request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, code));
        assertThat(verification.getResponse().getStatus()).isEqualTo(200);
        assertThat(verification.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(jsonMapper.readTree(verification.getResponse().getContentAsString())
                .get("accountStatus").asString()).isEqualTo("ACTIVE");
        assertThat(accounts.findById(accountId).orElseThrow().isEmailVerified()).isTrue();
        assertThat(otps.findById(accountId)).isEmpty();
        assertThat(events.findAll().stream().filter(event -> event.getAccountId().equals(accountId))).hasSize(1);
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, code)),
                409, "AUTH_EMAIL_ALREADY_VERIFIED");
    }

    @Test
    void duplicatePendingRegistrationDoesNotReplacePasswordOrProfileOrResendMail() throws Exception {
        String email = email();
        register(email);
        var account = accounts.findByEmail(email).orElseThrow();
        String hash = account.getPasswordHash();
        String otpHash = otps.findById(account.getId()).orElseThrow().getCodeHash();
        assertError(request("/register", new RegisterRequest(email.toUpperCase(), "other-password",
                "other-password", "Someone else")), 409, "AUTH_EMAIL_VERIFICATION_PENDING");
        assertThat(accounts.findByEmail(email).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(profiles.findById(account.getId()).orElseThrow().getDisplayName()).isEqualTo("Khanh");
        assertThat(otps.findById(account.getId()).orElseThrow().getCodeHash()).isEqualTo(otpHash);
    }

    @Test
    void verifiedAndDeletedEmailsRemainReserved() throws Exception {
        String email = email();
        register(email);
        request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, readCode(email)));
        assertError(register(email), 409, "AUTH_EMAIL_IN_USE");
        var account = accounts.findByEmail(email).orElseThrow();
        account.setDeleted(true);
        accounts.saveAndFlush(account);
        assertError(register(email), 409, "AUTH_EMAIL_IN_USE");
        assertError(request("/register/resend-otp", new ResendRegistrationOtpRequest(email)), 400, "AUTH_OTP_INVALID");
    }

    @Test
    void wrongOtpAttemptsPersistEvenThoughHttpReturnsAnErrorAndTheFifthAttemptLocksTheCode() throws Exception {
        String email = email();
        register(email);
        String code = readCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        UUID id = accounts.findByEmail(email).orElseThrow().getId();
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, wrong)),
                    attempt == 5 ? 429 : 400, attempt == 5 ? "AUTH_OTP_ATTEMPTS_EXCEEDED" : "AUTH_OTP_INVALID");
            assertThat(otps.findById(id).orElseThrow().getFailedAttempts()).isEqualTo(attempt);
        }
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, code)),
                429, "AUTH_OTP_ATTEMPTS_EXCEEDED");
        assertThat(accounts.findById(id).orElseThrow().isEmailVerified()).isFalse();
        clock.set(clock.instant().plusSeconds(60));
        assertThat(request("/register/resend-otp", new ResendRegistrationOtpRequest(email)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(otps.findById(id).orElseThrow().getFailedAttempts()).isZero();
        assertThat(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, readCode(email)))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void otpExpiresAtTheExactExpiryInstant() throws Exception {
        String email = email();
        register(email);
        String code = readCode(email);
        clock.set(clock.instant().plusSeconds(300));
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, code)),
                400, "AUTH_OTP_EXPIRED");
    }

    @Test
    void resendRequiresSixtySecondsAndReplacesTheOldCode() throws Exception {
        String email = email();
        register(email);
        String oldCode = readCode(email);
        clock.set(clock.instant().plusSeconds(59));
        assertError(request("/register/resend-otp", new ResendRegistrationOtpRequest(email)),
                429, "AUTH_OTP_RESEND_TOO_SOON");
        clock.set(clock.instant().plusSeconds(1));
        assertThat(request("/register/resend-otp", new ResendRegistrationOtpRequest(email)).getResponse().getStatus())
                .isEqualTo(200);
        String newCode = readCode(email);
        assertThat(newCode).isNotEqualTo(oldCode);
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, oldCode)),
                400, "AUTH_OTP_INVALID");
        assertThat(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email, newCode))
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void passwordConfirmationAndUtf8ByteLimitAreValidatedInBackend() throws Exception {
        String email = email();
        assertError(request("/register", new RegisterRequest(email, "test-password", "different-password", "Khanh")),
                400, "AUTH_PASSWORD_MISMATCH");
        assertError(request("/register", new RegisterRequest(email, "short", "short", "Khanh")),
                400, "AUTH_INVALID_REQUEST");
        String tooManyBytes = "ế".repeat(25);
        assertError(request("/register", new RegisterRequest(email, tooManyBytes, tooManyBytes, "Khanh")),
                400, "AUTH_PASSWORD_INVALID");
        assertError(request("/register", new RegisterRequest(email, "test-password", "test-password", "   ")),
                400, "AUTH_INVALID_REQUEST");
        assertThat(accounts.findByEmail(email)).isEmpty();
        String exactLimit = "a".repeat(72);
        assertThat(request("/register", new RegisterRequest(email, exactLimit, exactLimit, "Khanh"))
                .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void missingEmailAndMalformedOtpHaveStableErrors() throws Exception {
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email(), "123456")),
                400, "AUTH_OTP_INVALID");
        assertError(request("/register/verify-otp", new VerifyRegistrationOtpRequest(email(), "12345")),
                400, "AUTH_INVALID_REQUEST");
        assertError(request("/register/resend-otp", new ResendRegistrationOtpRequest(email())),
                400, "AUTH_OTP_INVALID");
    }

    @Test
    void simultaneousVerificationsActivateTheAccountAndCreateItsEventOnlyOnce() throws Exception {
        String email = email();
        register(email);
        String code = readCode(email);
        UUID accountId = accounts.findByEmail(email).orElseThrow().getId();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = List.of(
                    executor.submit(() -> verifyAfterStart(start, email, code)),
                    executor.submit(() -> verifyAfterStart(start, email, code)));
            start.countDown();
            assertThat(List.of(calls.get(0).get(10, TimeUnit.SECONDS), calls.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("VERIFIED", "AUTH_EMAIL_ALREADY_VERIFIED");
        }
        assertThat(events.findAll().stream().filter(event -> event.getAccountId().equals(accountId))).hasSize(1);
        assertThat(otps.findById(accountId)).isEmpty();
    }

    private String verifyAfterStart(CountDownLatch start, String email, String code) throws InterruptedException {
        start.await();
        try {
            service.verifyOtp(new VerifyRegistrationOtpRequest(email, code));
            return "VERIFIED";
        } catch (ApiException exception) {
            return exception.getErrorCode().name();
        }
    }

    private MvcResult register(String email) throws Exception {
        return request("/register", new RegisterRequest(email, "test-password", "test-password", "Khanh"));
    }

    private MvcResult request(String path, Object body) throws Exception {
        return mvc.perform(post("/api/auth" + path).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body))).andReturn();
    }

    private void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("code").asString()).isEqualTo(code);
        assertThat(response.has("password")).isFalse();
        assertThat(response.has("otp")).isFalse();
    }

    private String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private String readCode(String email) throws Exception {
        String baseUrl = "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025);
        try (HttpClient client = HttpClient.newHttpClient()) {
            String body = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            for (JsonNode message : jsonMapper.readTree(body).get("messages")) {
                if (message.get("To").get(0).get("Address").asString().equals(email)) {
                    String detail = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/message/"
                            + message.get("ID").asString())).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
                    Matcher matcher = Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])")
                            .matcher(jsonMapper.readTree(detail).get("Text").asString());
                    assertThat(matcher.find()).isTrue();
                    return matcher.group();
                }
            }
        }
        throw new AssertionError("Registration email was not received");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        MutableClock registrationTestClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-10-10T01:00:00Z"));

        void set(Instant value) { instant.set(value); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }
}
