package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.auth.response.DemoAccountResponse;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.service.DemoAccountService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.config.DataInitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.stream.Stream;

/**
 * Lets other services seed data for the shared demo accounts, whose ids differ per database.
 * Only the configured demo emails resolve, so the lookup cannot probe real players' emails.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.data-init.enabled", havingValue = "true")
public class DemoAccountServiceImpl implements DemoAccountService {
    private final DataInitProperties properties;
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public DemoAccountResponse findDemoAccount(String email) {
        String canonical = canonical(email);
        boolean isDemo = Stream.of(properties.admin(), properties.user(), properties.secondaryUser(), properties.unverifiedUser())
                .anyMatch(seed -> canonical(seed.email()).equals(canonical));
        if (!isDemo) throw new ApiException(ErrorCode.AUTH_NOT_FOUND);
        return accountRepository.findByEmailAndDeletedFalse(canonical)
                .map(account -> new DemoAccountResponse(account.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
    }

    private static String canonical(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
