package com.msspoker.rankingservice.service.client;

import java.util.List;
import java.util.UUID;

public interface AuthClient {
    /** Ids of the ADMIN accounts; throws RANK_DEPENDENCY_UNAVAILABLE when auth-service fails. */
    List<UUID> adminIds();
}
