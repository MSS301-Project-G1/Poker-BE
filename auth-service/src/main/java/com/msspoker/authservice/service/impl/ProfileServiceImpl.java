package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.dto.profile.response.MyProfileResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.mapper.ProfileMapper;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private static final int MAX_BATCH_SIZE = 100;
    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final Validator validator;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ProfileResponse> getProfilesBatch(List<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty() || accountIds.size() > MAX_BATCH_SIZE
                || accountIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
        List<UUID> uniqueIds = accountIds.stream().distinct().toList();
        var profilesById = profileRepository.findAllByAccountIdInAndAccount_DeletedFalse(uniqueIds).stream()
                .collect(Collectors.toMap(Profile::getAccountId, Function.identity()));
        // An IN query does not preserve request order; only return the public DTO fields.
        return uniqueIds.stream().map(profilesById::get).filter(Objects::nonNull)
                .map(profileMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile(UUID accountId) {
        Account account = activeAccount(accountId);
        Profile profile = profileRepository.findByAccountIdAndAccount_DeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
        return profileMapper.toMyProfileResponse(account, profile);
    }

    @Override
    @Transactional
    public MyProfileResponse updateMyProfile(UUID accountId, UpdateProfileRequest request) {
        Account account = activeAccount(accountId);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
        String phoneNumber = optionalText(request.phoneNumber());
        String countryCode = optionalText(request.countryCode());
        if ((phoneNumber != null && !phoneNumber.matches("\\+?[0-9]{7,15}"))
                || (countryCode != null && !countryCode.matches("[a-zA-Z]{2}"))
                || (request.birthDate() != null
                    && request.birthDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))))) {
            throw new ApiException(ErrorCode.AUTH_INVALID_REQUEST);
        }
        var normalized = new UpdateProfileRequest(request.displayName().strip(), optionalText(request.fullName()),
                phoneNumber, request.gender(), request.birthDate(), optionalText(request.bio()),
                countryCode == null ? null : countryCode.toUpperCase(Locale.ROOT), optionalText(request.city()));
        Profile profile = profileRepository.findByAccountIdAndAccount_DeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_NOT_FOUND));
        profileMapper.updateProfile(normalized, profile);
        account.setPhoneNumber(normalized.phoneNumber());
        accountRepository.save(account);
        profileRepository.save(profile);
        return profileMapper.toMyProfileResponse(account, profile);
    }

    private Account activeAccount(UUID accountId) {
        if (accountId == null) throw new ApiException(ErrorCode.AUTH_UNAUTHENTICATED);
        Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED));
        if (!account.isEmailVerified() || account.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.AUTH_EMAIL_VERIFICATION_PENDING);
        }
        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
        }
        return account;
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
