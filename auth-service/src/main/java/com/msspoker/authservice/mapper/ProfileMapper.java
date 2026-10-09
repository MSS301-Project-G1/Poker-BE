package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.entity.Profile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    ProfileResponse toResponse(Profile profile);
}
