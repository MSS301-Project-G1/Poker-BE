package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.dto.profile.response.MyProfileResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;

import java.util.List;
import java.util.UUID;

public interface ProfileService {
    List<ProfileResponse> getProfilesBatch(List<UUID> accountIds);
    MyProfileResponse getMyProfile(UUID accountId);
    MyProfileResponse updateMyProfile(UUID accountId, UpdateProfileRequest request);
}
