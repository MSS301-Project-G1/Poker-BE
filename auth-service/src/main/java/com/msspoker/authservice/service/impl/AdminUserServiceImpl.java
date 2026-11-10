package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.admin.request.BanUserRequest;
import com.msspoker.authservice.dto.admin.request.UnbanUserRequest;
import com.msspoker.authservice.dto.admin.response.AccountBanHistoryResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserPageResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.AccountBanEvent;
import com.msspoker.authservice.enums.AccountBanAction;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.AdminUserMapper;
import com.msspoker.authservice.repository.AccountBanEventRepository;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AdminUserService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.SessionGuard;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final AccountRepository accounts;
    private final AccountBanEventRepository events;
    private final RefreshTokenRepository tokens;
    private final AdminUserMapper mapper;
    private final SessionGuard sessions;
    private final Validator validator;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(UUID adminId, String keyword, Boolean banned, int page, int size) {
        requireAdmin(adminId);
        String term = keyword == null ? "" : keyword.strip();
        if (term.length() > 100) throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        var users = accounts.searchAdminUsers(term, banned, clock.instant(), pageable(page, size, "createdAt"));
        return new AdminUserPageResponse(users.getContent().stream().map(this::response).toList(),
                page, size, users.getTotalElements(), users.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID adminId, UUID accountId) {
        requireAdmin(adminId);
        return response(account(accountId));
    }

    @Override
    @Transactional
    public AdminUserResponse banUser(UUID adminId, UUID accountId, BanUserRequest request) {
        requireAdmin(adminId);
        validate(request);
        Account target = writableUser(adminId, accountId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant until = request.getUntil() == null ? null : request.getUntil().truncatedTo(ChronoUnit.MICROS);
        if (until != null && !until.isAfter(clock.instant())) throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        if (sessions.isBanned(target)) throw new ApiException(ErrorCode.AUTH_BAN_ALREADY_ACTIVE);
        String reason = request.getReason().strip();
        target.setBanned(true);
        target.setBannedUntil(until);
        target.setBanReason(reason);
        target.setBannedAt(now);
        target.setBannedBy(adminId);
        // Same account-first lock order as login/rotation. Unban never revives these credentials.
        tokens.revokeAllByAccountId(accountId, now);
        events.save(AccountBanEvent.builder().accountId(accountId).actorId(adminId).action(AccountBanAction.BAN)
                .reason(reason).until(until).occurredAt(now).build());
        return response(target);
    }

    @Override
    @Transactional
    public AdminUserResponse unbanUser(UUID adminId, UUID accountId, UnbanUserRequest request) {
        requireAdmin(adminId);
        validate(request);
        Account target = writableUser(adminId, accountId);
        // Repeating an unban is harmless and does not create duplicate history entries.
        if (!target.isBanned()) return response(target);
        target.setBanned(false);
        target.setBannedUntil(null);
        target.setBanReason(null);
        target.setBannedAt(null);
        target.setBannedBy(null);
        events.save(AccountBanEvent.builder().accountId(accountId).actorId(adminId).action(AccountBanAction.UNBAN)
                .reason(request.getReason().strip()).occurredAt(clock.instant()).build());
        return response(target);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBanHistoryResponse getBanHistory(UUID adminId, UUID accountId, int page, int size) {
        requireAdmin(adminId);
        account(accountId);
        var history = events.findByAccountId(accountId, pageable(page, size, "occurredAt"));
        return new AccountBanHistoryResponse(history.getContent().stream().map(mapper::toResponse).toList(),
                page, size, history.getTotalElements(), history.getTotalPages());
    }

    private Account account(UUID accountId) {
        return accounts.findByIdAndDeletedFalse(accountId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
    }

    private Account writableUser(UUID adminId, UUID accountId) {
        if (adminId.equals(accountId)) throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        Account target = accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
        if (target.getRole() == AccountRole.ADMIN) throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        return target;
    }

    private void requireAdmin(UUID adminId) {
        Account admin = accounts.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED));
        sessions.requireNotBanned(admin);
        if (admin.getRole() != AccountRole.ADMIN || !admin.isEmailVerified() || admin.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
    }

    private PageRequest pageable(int page, int size, String timeField) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, timeField, "id"));
    }

    private AdminUserResponse response(Account account) {
        AdminUserResponse response = mapper.toResponse(account);
        response.setBanned(sessions.isBanned(account));
        return response;
    }
}
