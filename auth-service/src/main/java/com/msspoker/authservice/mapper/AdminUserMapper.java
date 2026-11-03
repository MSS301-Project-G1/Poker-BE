package com.msspoker.authservice.mapper;

import com.msspoker.authservice.dto.admin.response.AccountBanEventResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.AccountBanEvent;
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
}
