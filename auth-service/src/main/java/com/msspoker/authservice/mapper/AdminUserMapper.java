package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.admin.response.AccountBanEventResponse;
import com.msspoker.authservice.dto.admin.response.AccountModerationEventResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserDetailResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.AccountBanEvent;
import com.msspoker.authservice.entity.AccountModerationEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdminUserMapper {
    @Mapping(target = "accountId", source = "id")
    @Mapping(target = "displayName", source = "profile.displayName")
    @Mapping(target = "playerCode", source = "profile.playerCode")
    @Mapping(target = "banned", ignore = true)
    AdminUserResponse toResponse(Account account);

    AccountBanEventResponse toResponse(AccountBanEvent event);

    @Mapping(target = "accountId", source = "id")
    @Mapping(target = "playerCode", source = "profile.playerCode")
    @Mapping(target = "displayName", source = "profile.displayName")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "gender", source = "profile.gender")
    @Mapping(target = "birthDate", source = "profile.birthDate")
    @Mapping(target = "bio", source = "profile.bio")
    @Mapping(target = "countryCode", source = "profile.countryCode")
    @Mapping(target = "city", source = "profile.city")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "customAvatar", ignore = true)
    @Mapping(target = "banned", ignore = true)
    AdminUserDetailResponse toDetail(Account account);

    AccountModerationEventResponse toResponse(AccountModerationEvent event);
}
