package com.msspoker.matchmakingservice.service;

import com.msspoker.matchmakingservice.dto.response.AccountRestrictionsResponse;

import java.util.UUID;

public interface RestrictionClient {
    AccountRestrictionsResponse restrictions(UUID accountId);
}
