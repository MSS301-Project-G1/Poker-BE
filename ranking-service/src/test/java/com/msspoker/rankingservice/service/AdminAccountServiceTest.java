package com.msspoker.rankingservice.service;

import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.service.client.AuthClient;
import com.msspoker.rankingservice.service.impl.AdminAccountServiceImpl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdminAccountServiceTest {
    final UUID admin = UUID.randomUUID();
    List<UUID> answer = List.of();
    boolean down;
    final AuthClient auth = () -> {
        if (down) throw RankingExceptions.authUnavailable();
        return answer;
    };
    final AdminAccountService service = new AdminAccountServiceImpl(auth);

    @Test
    void emptyUntilTheFirstRefreshThenTheAuthList() {
        assertTrue(service.adminIds().isEmpty());
        answer = List.of(admin);
        service.refresh();
        assertEquals(Set.of(admin), service.adminIds());
    }

    @Test
    void anAuthOutageKeepsTheLastKnownList() {
        answer = List.of(admin);
        service.refresh();
        down = true;
        service.refresh();
        assertEquals(Set.of(admin), service.adminIds());
    }
}
