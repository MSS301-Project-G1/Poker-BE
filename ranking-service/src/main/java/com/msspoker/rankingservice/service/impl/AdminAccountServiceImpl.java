package com.msspoker.rankingservice.service.impl;

import com.msspoker.rankingservice.exception.RankingApiException;
import com.msspoker.rankingservice.service.AdminAccountService;
import com.msspoker.rankingservice.service.client.AuthClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Roles never change after registration, so the admin list is read from auth-service in the background
 * instead of on every leaderboard request. A failed read keeps the last known list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {
    private final AuthClient auth;
    private volatile Set<UUID> admins = Set.of();

    @Override
    public Set<UUID> adminIds() {
        return admins;
    }

    @Scheduled(fixedDelayString = "${ranking.admins.refresh-millis:60000}")
    @Override
    public void refresh() {
        try {
            admins = Set.copyOf(auth.adminIds());
        } catch (RankingApiException ex) {
            log.warn("Admin accounts not refreshed; keeping the {} already known", admins.size(), ex);
        }
    }
}
