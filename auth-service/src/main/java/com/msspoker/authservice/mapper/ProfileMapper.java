package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.profile.request.UpdateProfileRequest;
import com.msspoker.authservice.dto.profile.response.MyProfileResponse;
import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProfileMapper {
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "avatarUrl", ignore = true)
    ProfileResponse toResponse(Profile profile);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "phoneNumber", source = "account.phoneNumber")
    @Mapping(target = "displayName", source = "profile.displayName")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "gender", source = "profile.gender")
    @Mapping(target = "birthDate", source = "profile.birthDate")
    @Mapping(target = "bio", source = "profile.bio")
    @Mapping(target = "countryCode", source = "profile.countryCode")
    @Mapping(target = "city", source = "profile.city")
    MyProfileResponse toMyProfileResponse(Account account, Profile profile);

    // PUT replaces editable fields, including clearing nulls; identity and avatar stay untouched.
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "displayName", source = "displayName")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "birthDate", source = "birthDate")
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "countryCode", source = "countryCode")
    @Mapping(target = "city", source = "city")
    void updateProfile(UpdateProfileRequest request, @MappingTarget Profile profile);
}
