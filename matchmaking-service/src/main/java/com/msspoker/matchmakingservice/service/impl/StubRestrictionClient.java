package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.dto.response.AccountRestrictionsResponse;
import com.msspoker.matchmakingservice.service.RestrictionClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "matchmaking.restriction.stub-enabled", havingValue = "true")
public class StubRestrictionClient implements RestrictionClient {
    @Override
    public AccountRestrictionsResponse restrictions(UUID accountId) {
        return new AccountRestrictionsResponse(false, null, null);
    }
}
