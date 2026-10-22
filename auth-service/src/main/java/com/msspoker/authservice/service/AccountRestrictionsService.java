package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.auth.response.AccountRestrictionsResponse;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountRestrictionsService {
    private final AccountRepository accounts;

    @Transactional(readOnly = true)
    public AccountRestrictionsResponse restrictions(UUID accountId) {
        var account = accounts.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
        return new AccountRestrictionsResponse(
                account.isBanned() || account.getAccountStatus() != AccountStatus.ACTIVE || !account.isEmailVerified(),
                account.getChatBannedUntil(), account.getRankBannedUntil());
    }
}
