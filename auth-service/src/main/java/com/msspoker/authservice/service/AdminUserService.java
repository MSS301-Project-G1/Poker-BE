package com.msspoker.authservice.service;

import com.msspoker.authservice.dto.admin.request.BanUserRequest;
import com.msspoker.authservice.dto.admin.request.UnbanUserRequest;
import com.msspoker.authservice.dto.admin.response.AccountBanHistoryResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserPageResponse;
import com.msspoker.authservice.dto.admin.response.AdminUserResponse;

import java.util.UUID;

public interface AdminUserService {
    AdminUserPageResponse getUsers(UUID adminId, String keyword, Boolean banned, int page, int size);
    AdminUserResponse getUser(UUID adminId, UUID accountId);
    AdminUserResponse banUser(UUID adminId, UUID accountId, BanUserRequest request);
    AdminUserResponse unbanUser(UUID adminId, UUID accountId, UnbanUserRequest request);
    AccountBanHistoryResponse getBanHistory(UUID adminId, UUID accountId, int page, int size);
}
