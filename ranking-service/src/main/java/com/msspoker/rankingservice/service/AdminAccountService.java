package com.msspoker.rankingservice.service;

import java.util.Set;
import java.util.UUID;

public interface AdminAccountService {
    /** ADMIN accounts kept off the leaderboards; empty until auth-service first answers. */
    Set<UUID> adminIds();

    void refresh();
}
