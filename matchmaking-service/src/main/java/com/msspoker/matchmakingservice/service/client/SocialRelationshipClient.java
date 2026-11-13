package com.msspoker.matchmakingservice.service.client;

import java.util.UUID;

public interface SocialRelationshipClient {
    boolean friends(UUID actor, UUID other);
}
