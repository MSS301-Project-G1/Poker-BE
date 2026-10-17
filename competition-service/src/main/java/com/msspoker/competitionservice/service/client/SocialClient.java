package com.msspoker.competitionservice.service.client;

import com.msspoker.competitionservice.dto.response.FriendshipResponse;

import java.util.UUID;

public interface SocialClient {
    FriendshipResponse friendship(UUID accountId, UUID otherId);
}
