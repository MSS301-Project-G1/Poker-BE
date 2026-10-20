package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.profile.response.AdminAvatarPresetResponse;
import com.msspoker.authservice.dto.profile.response.AvatarOptionResponse;
import com.msspoker.authservice.entity.AvatarPreset;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AvatarPresetMapper {
    @Mapping(target = "avatarId", source = "id")
    @Mapping(target = "avatarUrl", ignore = true)
    AvatarOptionResponse toOption(AvatarPreset avatarPreset);

    @Mapping(target = "avatarId", source = "id")
    @Mapping(target = "avatarUrl", ignore = true)
    AdminAvatarPresetResponse toAdminResponse(AvatarPreset avatarPreset);
}
