package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.admin.request.BanUserRequest;
import com.msspoker.authservice.dto.admin.request.ModerateUserRequest;
import com.msspoker.authservice.dto.admin.request.UnbanUserRequest;
import com.msspoker.authservice.dto.admin.response.AccountBanHistoryResponse;
import com.msspoker.authservice.dto.admin.response.AccountModerationHistoryResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserDetailResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserPageResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;
import com.msspoker.authservice.dto.auth.request.ResendRegistrationOtpRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.AccountBanEvent;
import com.msspoker.authservice.entity.AccountModerationEvent;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountBanAction;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.AdminUserMapper;
import com.msspoker.authservice.repository.AccountBanEventRepository;
import com.msspoker.authservice.repository.AccountModerationEventRepository;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AdminUserService;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.SessionGuard;
import com.msspoker.authservice.shared.infrastructure.storage.S3AvatarStorage;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    /** Neutral name after a reset; the unique player code keeps it distinct. */
    private static final String RESET_NAME_PREFIX = "Player ";
    private final AccountRepository accounts;
    private final AccountBanEventRepository events;
    private final AccountModerationEventRepository moderationEvents;
    private final ProfileRepository profiles;
    private final RefreshTokenRepository tokens;
    private final AuthService authService;
    private final S3AvatarStorage avatars;
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
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetails(UUID adminId, UUID accountId) {
        requireAdmin(adminId);
        return detail(account(accountId));
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

    /**
     * One moderation action on a player (never oneself or another ADMIN). The change and its history row
     * commit together; recovery and verification emails are sent before the commit, like the public flows.
     */
    @Override
    @Transactional
    public AdminUserDetailResponse moderate(UUID adminId, UUID accountId, ModerateUserRequest request) {
        requireAdmin(adminId);
        validate(request);
        Account target = writableUser(adminId, accountId);
        Instant now = clock.instant();
        String previous = switch (request.getAction()) {
            case RESET_DISPLAY_NAME -> resetDisplayName(accountId);
            case REMOVE_AVATAR -> removeAvatar(accountId);
            case CLEAR_BIO -> clearBio(accountId);
            case REVOKE_SESSIONS -> {
                tokens.revokeAllByAccountId(accountId, now);
                yield null;
            }
            case SEND_PASSWORD_RESET -> {
                authService.sendPasswordReset(accountId);
                yield null;
            }
            case RESEND_VERIFICATION -> {
                authService.resendOtp(new ResendRegistrationOtpRequest(target.getEmail()));
                yield null;
            }
        };
        moderationEvents.save(AccountModerationEvent.builder().accountId(accountId).actorId(adminId)
                .action(request.getAction()).reason(request.getReason().strip()).previousValue(previous)
                .occurredAt(now).build());
        return detail(target);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountModerationHistoryResponse getModerationHistory(UUID adminId, UUID accountId, int page, int size) {
        requireAdmin(adminId);
        account(accountId);
        var history = moderationEvents.findByAccountId(accountId, pageable(page, size, "occurredAt"));
        return new AccountModerationHistoryResponse(history.getContent().stream().map(mapper::toResponse).toList(),
                page, size, history.getTotalElements(), history.getTotalPages());
    }

    private String resetDisplayName(UUID accountId) {
        Profile profile = profileForUpdate(accountId);
        String previous = profile.getDisplayName();
        String neutral = RESET_NAME_PREFIX + profile.getPlayerCode();
        if (neutral.equals(previous)) throw new ApiException(ErrorCode.AUTH_CONFLICT);
        profile.setDisplayName(neutral);
        return previous;
    }

    /** Back to the default avatar; an uploaded image is deleted from S3 only after the change commits. */
    private String removeAvatar(UUID accountId) {
        Profile profile = profileForUpdate(accountId);
        if (profile.getAvatarPreset() == null && profile.getAvatarUrl() == null) throw new ApiException(ErrorCode.AUTH_CONFLICT);
        String previous = profile.getAvatarPreset() == null ? null : profile.getAvatarPreset().getName();
        String uploaded = profile.getAvatarUrl();
        profile.setAvatarPreset(null);
        profile.setAvatarUrl(null);
        if (uploaded != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    avatars.deleteOwned(accountId, uploaded);
                }
            });
        }
        return previous;
    }

    private String clearBio(UUID accountId) {
        Profile profile = profileForUpdate(accountId);
        if (profile.getBio() == null || profile.getBio().isBlank()) throw new ApiException(ErrorCode.AUTH_CONFLICT);
        String previous = profile.getBio();
        profile.setBio(null);
        return previous;
    }

    /** Locked after the account, in the same order as the player's own profile update. */
    private Profile profileForUpdate(UUID accountId) {
        return profiles.findByAccountIdForUpdate(accountId).orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
    }

    private AdminUserDetailResponse detail(Account account) {
        AdminUserDetailResponse response = mapper.toDetail(account);
        response.setBanned(sessions.isBanned(account));
        Profile profile = account.getProfile();
        if (profile != null) {
            response.setCustomAvatar(profile.getAvatarPreset() != null || profile.getAvatarUrl() != null);
            response.setAvatarUrl(avatars.resolveViewUrl(profile.getAvatarPreset() == null
                    ? profile.getAvatarUrl() : profile.getAvatarPreset().getObjectKey()));
        }
        return response;
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
