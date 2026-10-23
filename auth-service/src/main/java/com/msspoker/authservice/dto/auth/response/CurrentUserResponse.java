package com.msspoker.authservice.dto.auth.response;

import com.msspoker.authservice.dto.profile.response.ProfileResponse;
import com.msspoker.authservice.enums.AccountRole;

import java.util.UUID;

public record CurrentUserResponse(UUID accountId, String email, AccountRole role, ProfileResponse profile) {
}
