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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
        var event = RatingTestSupport.rank(RatingTestSupport.accounts(6), 100);
        rating.matchFinished(event);
        doThrow(new ResourceAccessException("wallet down")).when(wallet).credit(any());

        dispatcher.dispatchPending();
        List<RewardCreditEntity> pending = rewards.findAll();
        assertTrue(pending.stream().allMatch(reward -> reward.getCreditedAt() == null));
        // An outage stops the batch at the first failure.
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

    @Test
    void aRefusedRewardDoesNotHoldBackTheOthers() {
        rating.matchFinished(RatingTestSupport.rank(RatingTestSupport.accounts(6), 100));
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST)).when(wallet)
                .credit(argThat(request -> request.amount() == 300));

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        Map<Long, RewardCreditEntity> byAmount = rewards.findAll().stream()
                .collect(Collectors.toMap(RewardCreditEntity::getAmount, Function.identity()));
        assertNull(byAmount.get(300L).getCreditedAt());
        assertEquals(2, byAmount.get(300L).getAttempts());
        assertNotNull(byAmount.get(200L).getCreditedAt());
        assertNotNull(byAmount.get(100L).getCreditedAt());
        // The refused reward is retried; the credited ones are not sent again.
        verify(wallet, times(4)).credit(any());
    }
}
