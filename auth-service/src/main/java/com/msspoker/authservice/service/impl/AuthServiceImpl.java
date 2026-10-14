package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.dto.auth.response.CurrentUserResponse;
import com.msspoker.authservice.dto.auth.response.SessionResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.RefreshToken;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.ProfileMapper;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.AuthJwtProperties;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProperties;
    private final Validator validator;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private volatile String dummyPasswordHash;

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
        Account account = accountRepository.findByEmail(email).orElse(null);
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
        // Serialize rotation/logout for the matching token. A concurrent refresh can win only once.
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
        Account account = accountRepository.findByIdAndDeletedFalse(token.getAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID));
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
                profileRepository.findById(accountId).map(profileMapper::toResponse).orElse(null));
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
}
