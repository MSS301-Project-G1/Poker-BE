package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.entity.RefreshToken;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionGuard {
    private final RefreshTokenRepository tokens;
    private final AccountRepository accounts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void verify(Jwt jwt, String tabId) {
        UUID sid;
        try {
            sid = UUID.fromString(jwt.getClaimAsString("sid"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.AUTH_SESSION_REPLACED);
        }
        var token = tokens.findById(sid).orElseThrow(() -> new ApiException(ErrorCode.AUTH_SESSION_REPLACED));
        if (!token.getAccountId().toString().equals(jwt.getSubject())) throw new ApiException(ErrorCode.AUTH_SESSION_REPLACED);
        var account = accounts.findByIdAndDeletedFalse(token.getAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED));
        requireNotBanned(account);
        if (token.isReplaced()) throw new ApiException(ErrorCode.AUTH_SESSION_REPLACED);
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
        if (!account.isEmailVerified() || account.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_VERIFICATION_PENDING);
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
        }
        requireTab(token, tabId);
    }

    public void requireLive(RefreshToken token) {
        if (token.isReplaced()) throw new ApiException(ErrorCode.AUTH_SESSION_REPLACED);
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID);
        }
    }

    public boolean isBanned(Account account) {
        return account.isBanned() && (account.getBannedUntil() == null || account.getBannedUntil().isAfter(clock.instant()));
    }

    public void requireNotBanned(Account account) {
        if (isBanned(account)) throw new ApiException(ErrorCode.AUTH_ACCOUNT_BANNED);
    }

    public void requireTab(RefreshToken token, String tabId) {
        if (token.getActiveTabId() == null || !token.getActiveTabId().toString().equals(tabId)) {
            throw new ApiException(ErrorCode.AUTH_TAB_REPLACED);
        }
    }
}
