package com.msspoker.competitionservice.service.impl;

import com.msspoker.competitionservice.dto.response.FriendshipResponse;
import com.msspoker.competitionservice.service.SocialClient;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** SPEC §9.4 mock while social-service is unavailable locally: everyone is a friend. */
@Component
@Profile("dev | test")
public class DevSocialClientImpl implements SocialClient {
    @Override
    public FriendshipResponse friendship(UUID accountId, UUID otherId) {
        return new FriendshipResponse(true, false);
    }
}
