package com.msspoker.authservice.service.impl;

import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.mapper.ProfileMapper;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private static final int MAX_BATCH_SIZE = 100;
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

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
}
