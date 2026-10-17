package com.msspoker.rankingservice.service.job;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.entity.RewardCreditEntity;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.RatingService;
import com.msspoker.rankingservice.service.RatingTestSupport;
import com.msspoker.rankingservice.service.client.WalletClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class RewardDispatcherTest {
    @Autowired RatingService rating;
    @Autowired RewardDispatcher dispatcher;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    @MockitoBean WalletClient wallet;

    @BeforeEach
    void clean() {
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
    }

    @Test
    void walletOutageKeepsRewardPendingAndRetriesWithSameKey() {
        var event = RatingTestSupport.rank(RatingTestSupport.accounts(3), 100);
        rating.matchFinished(event);
        doThrow(new ResourceAccessException("wallet down")).when(wallet).credit(any());

        dispatcher.dispatchPending();
        List<RewardCreditEntity> pending = rewards.findAll();
        assertTrue(pending.stream().allMatch(reward -> reward.getCreditedAt() == null));
        // The batch stops at the first failure so rewards are retried in order.
        assertEquals(1, pending.stream().mapToInt(RewardCreditEntity::getAttempts).sum());

        reset(wallet);
        dispatcher.dispatchPending();
        List<RewardCreditEntity> credited = rewards.findAll().stream()
                .sorted(Comparator.comparingInt(RewardCreditEntity::getPlace)).toList();
        assertTrue(credited.stream().allMatch(reward -> reward.getCreditedAt() != null));
        assertEquals(List.of(1, 1, 2), credited.stream().map(RewardCreditEntity::getAttempts).sorted().toList());

        var sent = org.mockito.ArgumentCaptor.forClass(WalletCreditRequest.class);
        verify(wallet, times(3)).credit(sent.capture());
        WalletCreditRequest first = sent.getAllValues().stream().filter(r -> r.amount() == 300).findFirst().orElseThrow();
        assertEquals("RANK_REWARD", first.reason());
        assertEquals(event.matchId().toString(), first.refId());
        assertEquals(credited.getFirst().getIdempotencyKey(), first.idempotencyKey());
    }
}
