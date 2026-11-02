package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.RefreshToken;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import java.time.Instant;
import java.util.UUID;

final class TestSessions {
    static final UUID TAB = UUID.fromString("10000000-0000-4000-8000-000000000001");

    static String issue(JwtService jwt, RefreshTokenRepository tokens, Account account, Instant expiry) {
        var token = tokens.saveAndFlush(RefreshToken.builder().accountId(account.getId()).activeTabId(TAB)
                .tokenHash(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                .createdAt(expiry.minusSeconds(3600)).expiresAt(expiry).build());
        return jwt.issue(account, token.getId(), expiry);
    }
}
