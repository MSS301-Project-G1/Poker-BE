package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.auth.request.ChangePasswordRequest;
import com.msspoker.authservice.dto.auth.request.ForgotPasswordRequest;
import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.request.RegisterRequest;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.request.ResetPasswordRequest;
import com.msspoker.authservice.dto.auth.request.VerifyRegistrationOtpRequest;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.dto.auth.response.EmailVerificationResponse;
import com.msspoker.authservice.dto.auth.response.RegistrationResponse;
import com.msspoker.authservice.dto.auth.response.SessionResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.PasswordResetOtp;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.entity.RefreshToken;
import com.msspoker.authservice.entity.RegistrationEvent;
import com.msspoker.authservice.entity.RegistrationOtp;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.ProfileMapper;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.PasswordResetOtpRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.repository.RegistrationEventRepository;
import com.msspoker.authservice.repository.RegistrationOtpRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.common.exception.OtpVerificationException;
import com.msspoker.authservice.shared.infrastructure.config.AuthJwtProperties;
import com.msspoker.authservice.shared.infrastructure.config.OtpProperties;
import com.msspoker.authservice.shared.infrastructure.mail.AuthMailSender;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;
import com.msspoker.authservice.shared.infrastructure.storage.S3AvatarStorage;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RegistrationOtpRepository otpRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final RegistrationEventRepository eventRepository;
    private final AuthMailSender mailSender;
    private final OtpProperties otpProperties;
    private final ProfileMapper profileMapper;
    private final S3AvatarStorage s3Storage;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProperties;
    private final Validator validator;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private volatile String dummyPasswordHash;

    @Override
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        validate(request);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        validateNewPassword(request.password(), request.confirmPassword());
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

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        validate(request);
        Account account = accountRepository.findByEmailForUpdate(request.email().strip().toLowerCase(Locale.ROOT))
                .filter(this::canResetPassword).orElse(null);
        if (account == null) return;
        PasswordResetOtp otp = passwordResetOtpRepository.findById(account.getId()).orElse(null);
        Instant now = clock.instant();
        // A throttled request has the same public response as an unknown email.
        if (otp != null && now.isBefore(otp.getLastSentAt().plus(otpProperties.resendCooldown()))) return;
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (otp != null && passwordEncoder.matches(code, otp.getCodeHash()));
        String codeHash = passwordEncoder.encode(code);
        try {
            mailSender.sendPasswordResetOtp(account.getEmail(), code, otpProperties.ttl());
        } catch (ApiException exception) {
            if (exception.getErrorCode() != ErrorCode.AUTH_MAIL_UNAVAILABLE) throw exception;
            // Preserve the previous OTP and the generic response; never log mail content or recipient.
            log.warn("Password reset email could not be sent; recovery OTP was left unchanged");
            return;
        }
        if (otp == null) otp = PasswordResetOtp.builder().account(account).build();
        otp.setCodeHash(codeHash);
        otp.setExpiresAt(clock.instant().plus(otpProperties.ttl()));
        otp.setLastSentAt(clock.instant());
        otp.setFailedAttempts(0);
        passwordResetOtpRepository.save(otp);
    }

    @Override
    @Transactional(noRollbackFor = OtpVerificationException.class)
    public void resetPassword(ResetPasswordRequest request) {
        validate(request);
        validateNewPassword(request.password(), request.confirmPassword());
        Account account = accountRepository.findByEmailForUpdate(request.email().strip().toLowerCase(Locale.ROOT))
                .filter(this::canResetPassword).orElseThrow(() -> new ApiException(ErrorCode.AUTH_OTP_INVALID));
        PasswordResetOtp otp = passwordResetOtpRepository.findById(account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_OTP_INVALID));
        Instant now = clock.instant();
        if (!otp.getExpiresAt().isAfter(now)) throw new ApiException(ErrorCode.AUTH_OTP_EXPIRED);
        if (otp.getFailedAttempts() >= otpProperties.maxAttempts()) {
            throw new OtpVerificationException(ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED);
        }
        if (!passwordEncoder.matches(request.otp(), otp.getCodeHash())) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            throw new OtpVerificationException(otp.getFailedAttempts() >= otpProperties.maxAttempts()
                    ? ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED : ErrorCode.AUTH_OTP_INVALID);
        }
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        passwordResetOtpRepository.delete(otp);
        // Password, OTP consumption and revocation of every device commit atomically.
        refreshTokenRepository.revokeAllByAccountId(account.getId(), now);
    }

    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordRequest request) {
        validate(request);
        validateNewPassword(request.newPassword(), request.confirmPassword());
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED));
        requireActive(account);
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_CURRENT_PASSWORD_INVALID);
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        // A recovery code issued before this change must not reset the new password later.
        passwordResetOtpRepository.deleteById(account.getId());
        refreshTokenRepository.revokeAllByAccountId(account.getId(), clock.instant());
    }

    @Override
    @Transactional
    public SessionTokens login(LoginRequest request, String previousRefreshToken) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmailForUpdate(email).orElse(null);
        // Perform a password check for unknown emails too; use the same outward error.
        String hash = account == null ? dummyHash() : account.getPasswordHash();
        if (!passwordEncoder.matches(request.password(), hash) || account == null || account.isDeleted()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        requireActive(account);
        revoke(previousRefreshToken);

        Instant now = clock.instant();
        String rawRefreshToken = newRefreshToken();
        RefreshToken token = RefreshToken.builder()
                .accountId(account.getId()).tokenHash(hash(rawRefreshToken)).createdAt(now)
                .expiresAt(now.plus(jwtProperties.refreshTtl())).build();
        refreshTokenRepository.save(token);
        return issueTokens(account, token, rawRefreshToken);
    }

    @Override
    @Transactional
    public SessionTokens refresh(String rawRefreshToken) {
        if (!validFormat(rawRefreshToken)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        // Always lock account before token, matching password reset and avoiding lock-order cycles.
        UUID accountId = refreshTokenRepository.findAccountIdByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        // Serialize rotation/logout for the matching token. A concurrent refresh can win only once.
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        requireActive(account);
        String replacement = newRefreshToken();
        token.setTokenHash(hash(replacement));
        // Preserve the original seven-day deadline; rotation does not prolong the login.
        return issueTokens(account, token, replacement);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        revoke(rawRefreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID accountId) {
        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED));
        requireActive(account);
        return new CurrentUserResponse(account.getId(), account.getEmail(), account.getRole(),
                profileRepository.findById(accountId).map(profile -> {
                    var response = profileMapper.toResponse(profile);
                    response.setAvatarUrl(s3Storage.resolveViewUrl(profile.getAvatarUrl()));
                    return response;
                }).orElse(null));
    }

    private void requireActive(Account account) {
        if (!account.isEmailVerified() || account.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_VERIFICATION_PENDING);
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
        }
    }

    private SessionTokens issueTokens(Account account, RefreshToken refreshToken, String rawRefreshToken) {
        Instant accessExpiresAt = clock.instant().plus(jwtProperties.accessTtl());
        if (accessExpiresAt.isAfter(refreshToken.getExpiresAt())) {
            accessExpiresAt = refreshToken.getExpiresAt();
        }
        return new SessionTokens(jwtService.issue(account, accessExpiresAt), rawRefreshToken,
                new SessionResponse(account.getId(), account.getEmail(), account.getRole(),
                        accessExpiresAt, refreshToken.getExpiresAt()));
    }

    private void revoke(String rawRefreshToken) {
        if (!validFormat(rawRefreshToken)) return;
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) token.setRevokedAt(clock.instant());
        });
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean validFormat(String rawToken) {
        return rawToken != null && rawToken.matches("[A-Za-z0-9_-]{43}");
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String dummyHash() {
        if (dummyPasswordHash == null) {
            synchronized (this) {
                if (dummyPasswordHash == null) dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
            }
        }
        return dummyPasswordHash;
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
        mailSender.sendRegistrationOtp(account.getEmail(), code, otpProperties.ttl());
    }

    private RegistrationResponse registrationResponse(Account account, RegistrationOtp otp) {
        return RegistrationResponse.builder().accountId(account.getId()).email(account.getEmail())
                .accountStatus(account.getAccountStatus()).otpExpiresAt(otp.getExpiresAt())
                .resendAvailableAt(otp.getLastSentAt().plus(otpProperties.resendCooldown())).build();
    }

    private boolean canResetPassword(Account account) {
        return !account.isDeleted() && account.isEmailVerified() && account.getAccountStatus() == AccountStatus.ACTIVE;
    }

    private void validateNewPassword(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        // BCrypt limits bytes, rather than Java characters. Do not trim passwords.
        if (password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
    }
}
