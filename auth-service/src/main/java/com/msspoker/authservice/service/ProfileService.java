package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.profile.request.AvatarPresignRequest;
import com.msspoker.authservice.dto.profile.request.ConfirmAvatarRequest;
import com.msspoker.authservice.dto.profile.request.CreateAvatarPresetRequest;
import com.msspoker.authservice.dto.profile.request.UpdateAvatarPresetRequest;
import com.msspoker.authservice.dto.profile.request.SelectAvatarRequest;
import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.dto.profile.response.AvatarOptionResponse;
import com.msspoker.authservice.dto.profile.response.AvatarPresignResponse;
import com.msspoker.authservice.dto.profile.response.AdminAvatarPresetResponse;
import com.msspoker.authservice.dto.profile.response.MyProfileResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.dto.profile.response.PlayerResponse;

import java.util.List;
import java.util.UUID;

public interface ProfileService {
    List<PlayerResponse> searchPlayers(UUID actor, String query);
    PlayerResponse getMyPlayer(UUID actor);
    List<ProfileResponse> getProfilesBatch(List<UUID> accountIds);
    MyProfileResponse getMyProfile(UUID accountId);
    MyProfileResponse updateMyProfile(UUID accountId, UpdateProfileRequest request);
    List<AvatarOptionResponse> getPresets();
    ProfileResponse selectPreset(UUID accountId, SelectAvatarRequest request);
    AvatarPresignResponse presign(UUID accountId, AvatarPresignRequest request);
    ProfileResponse confirmUpload(UUID accountId, ConfirmAvatarRequest request);
    List<AdminAvatarPresetResponse> getAdminPresets(UUID adminId);
    AvatarPresignResponse presignPreset(UUID adminId, AvatarPresignRequest request);
    AdminAvatarPresetResponse createPreset(UUID adminId, CreateAvatarPresetRequest request);
    AdminAvatarPresetResponse updatePreset(UUID adminId, UUID avatarId, UpdateAvatarPresetRequest request);
}
