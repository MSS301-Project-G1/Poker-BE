package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.RegistrationService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

// A mocked mail sender cannot expose JavaMailSenderImpl's SMTP health check.
@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class RegistrationRollbackTests {
    @Autowired private RegistrationService service;
    @Autowired private AccountRepository accounts;
    @Autowired private RegistrationOtpRepository otps;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JavaMailSender mailSender;

    @BeforeEach
    void setup() {
        org.mockito.Mockito.reset(mailSender);
    }

    @Test
    void smtpFailureRollsBackRegistration() {
        String email = UUID.randomUUID() + "@example.com";
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.register(new RegisterRequest(email, "test-password", "test-password", "Khanh")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_MAIL_UNAVAILABLE));
        assertThat(accounts.findByEmail(email)).isEmpty();
    }

    @Test
    void smtpFailureDuringResendPreservesPreviousCodeAndAttemptCount() {
        String email = UUID.randomUUID() + "@example.com";
        UUID id = service.register(new RegisterRequest(email, "test-password", "test-password", "Khanh")).getAccountId();
        jdbc.update("UPDATE registration_otps SET last_sent_at = last_sent_at - INTERVAL '61 seconds', failed_attempts = 2 WHERE account_id = ?", id);
        String previousHash = otps.findById(id).orElseThrow().getCodeHash();
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> service.resendOtp(new ResendRegistrationOtpRequest(email)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_MAIL_UNAVAILABLE));
        var previous = otps.findById(id).orElseThrow();
        assertThat(previous.getCodeHash()).isEqualTo(previousHash);
        assertThat(previous.getFailedAttempts()).isEqualTo(2);
    }
}
