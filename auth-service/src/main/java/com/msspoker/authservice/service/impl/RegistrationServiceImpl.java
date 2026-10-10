package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.RegistrationService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.common.exception.OtpVerificationException;
import com.msspoker.authservice.shared.infrastructure.config.OtpProperties;
import com.msspoker.authservice.shared.infrastructure.mail.RegistrationMailSender;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final RegistrationOtpRepository otpRepository;
    private final RegistrationEventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationMailSender mailSender;
    private final OtpProperties otpProperties;
    private final Validator validator;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        validate(request);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        // BCrypt limits bytes, rather than Java characters. Do not trim passwords.
        if (request.password().codePointCount(0, request.password().length()) < 8
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
        accountRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.isDeleted() && !existing.isEmailVerified()
                    && existing.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
                throw new ApiException(ErrorCode.AUTH_EMAIL_VERIFICATION_PENDING);
            }
            throw new ApiException(ErrorCode.AUTH_EMAIL_IN_USE);
        });

        Account account = accountRepository.saveAndFlush(Account.builder()
                .email(email).passwordHash(passwordEncoder.encode(request.password())).build());
        profileRepository.save(Profile.builder().account(account).displayName(request.displayName().strip()).build());
        RegistrationOtp otp = RegistrationOtp.builder().account(account).build();
        issueOtp(account, otp);
        otpRepository.save(otp);
        return registrationResponse(account, otp);
    }

    @Override
    @Transactional(noRollbackFor = OtpVerificationException.class)
    public EmailVerificationResponse verifyOtp(VerifyRegistrationOtpRequest request) {
        validate(request);
        Account account = pendingAccount(request.email());
        RegistrationOtp otp = otpRepository.findById(account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_OTP_INVALID));
        Instant now = clock.instant();
        if (!otp.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_OTP_EXPIRED);
        }
        if (otp.getFailedAttempts() >= otpProperties.maxAttempts()) {
            throw new OtpVerificationException(ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED);
        }
        if (!passwordEncoder.matches(request.otp(), otp.getCodeHash())) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            throw new OtpVerificationException(otp.getFailedAttempts() >= otpProperties.maxAttempts()
                    ? ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED : ErrorCode.AUTH_OTP_INVALID);
        }

        account.setEmailVerified(true);
        account.setAccountStatus(AccountStatus.ACTIVE);
        otpRepository.delete(otp);
        // Account activation and its one registration event commit atomically.
        eventRepository.save(RegistrationEvent.builder().accountId(account.getId()).occurredAt(now).build());
        return EmailVerificationResponse.builder().accountId(account.getId()).email(account.getEmail())
                .accountStatus(account.getAccountStatus()).build();
    }

    @Override
    @Transactional
    public RegistrationResponse resendOtp(ResendRegistrationOtpRequest request) {
        validate(request);
        Account account = pendingAccount(request.email());
        RegistrationOtp otp = otpRepository.findById(account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_OTP_INVALID));
        if (clock.instant().isBefore(otp.getLastSentAt().plus(otpProperties.resendCooldown()))) {
            throw new ApiException(ErrorCode.AUTH_OTP_RESEND_TOO_SOON);
        }
        issueOtp(account, otp);
        return registrationResponse(account, otp);
    }

    private Account pendingAccount(String rawEmail) {
        Account account = accountRepository.findByEmailForUpdate(rawEmail.strip().toLowerCase(Locale.ROOT))
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_OTP_INVALID));
        if (account.isEmailVerified()) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_ALREADY_VERIFIED);
        }
        if (account.getAccountStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.AUTH_OTP_INVALID);
        }
        return account;
    }

    private void issueOtp(Account account, RegistrationOtp otp) {
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (otp.getCodeHash() != null && passwordEncoder.matches(code, otp.getCodeHash()));
        Instant now = clock.instant();
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plus(otpProperties.ttl()));
        otp.setLastSentAt(now);
        otp.setFailedAttempts(0);
        // SMTP failure rolls back the new account or OTP replacement; the previous code stays valid.
        mailSender.sendOtp(account.getEmail(), code, otpProperties.ttl());
    }

    private RegistrationResponse registrationResponse(Account account, RegistrationOtp otp) {
        return RegistrationResponse.builder().accountId(account.getId()).email(account.getEmail())
                .accountStatus(account.getAccountStatus()).otpExpiresAt(otp.getExpiresAt())
                .resendAvailableAt(otp.getLastSentAt().plus(otpProperties.resendCooldown())).build();
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
    }
}
