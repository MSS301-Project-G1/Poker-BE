package com.msspoker.rankingservice.service.client;

import java.util.List;
import java.util.UUID;

public interface SocialClient {
    /** Friend account ids of the player; throws RANK_DEPENDENCY_UNAVAILABLE when social-service fails. */
    List<UUID> friends(UUID accountId);
}
