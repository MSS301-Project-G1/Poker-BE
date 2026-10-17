package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.exception.InsufficientBalanceException;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import com.msspoker.matchmakingservice.service.WalletClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankMatchCoordinatorImplTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    private final RankProposalStateService state = mock(RankProposalStateService.class);
    private final WalletClient wallet = mock(WalletClient.class);
    private final GameClient game = mock(GameClient.class);
    private final QueueStore queueStore = mock(QueueStore.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final RankMatchCoordinatorImpl coordinator = new RankMatchCoordinatorImpl(
            state, wallet, game, queueStore, properties, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void debitsEveryoneThenCreatesRankTable() {
        UUID proposalId = UUID.randomUUID();
        QueueEntry first = entry(1, 1000);
        QueueEntry second = entry(2, 1010);
        var work = work(proposalId, first, second);
        UUID tableId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        when(state.work(proposalId)).thenReturn(work);
        when(game.createTable(CreateTableRequest.rank(
                proposalId, List.of(first.accountId(), second.accountId()), 100
        ))).thenReturn(new TableCreatedResponse(tableId, matchId));

        coordinator.start(proposalId);

        verify(wallet).debit(fee(proposalId, first.accountId()));
        verify(wallet).debit(fee(proposalId, second.accountId()));
        verify(state).feeCharged(proposalId, first.accountId());
        verify(state).feeCharged(proposalId, second.accountId());
        verify(queueStore).complete(eq(List.of(first, second)), any());
        verify(state).ready(proposalId, tableId, matchId);
    }

    @Test
    void insufficientBalanceRefundsChargedPlayersAndDoesNotCreateTable() {
        UUID proposalId = UUID.randomUUID();
        QueueEntry first = entry(1, 1000);
        QueueEntry insufficient = entry(2, 1010);
        when(state.work(proposalId)).thenReturn(work(proposalId, first, insufficient));
        doAnswer(invocation -> {
            WalletTransferRequest request = invocation.getArgument(0);
            if (request.accountId().equals(insufficient.accountId())) {
                throw new InsufficientBalanceException("insufficient", null);
            }
            return null;
        }).when(wallet).debit(any());

        coordinator.start(proposalId);

        verify(state).failed(proposalId, "INSUFFICIENT_BALANCE", insufficient.accountId());
        verify(queueStore).discard(List.of(first, insufficient));
        verify(queueStore).requeue(List.of(first));
        verify(queueStore).startRankCooldown(insufficient.accountId(), NOW.plusSeconds(120));
        verify(state).redisReconciled(proposalId);
        verify(wallet).refund(new WalletTransferRequest(
                first.accountId(), 100, "RANK_REFUND", proposalId.toString(),
                "rank-refund:" + proposalId + ":" + first.accountId()
        ));
        verify(game, never()).createTable(any());
    }

    private RankProposalStateService.Work work(UUID proposalId, QueueEntry... entries) {
        List<QueueEntry> list = List.of(entries);
        return new RankProposalStateService.Work(
                proposalId,
                100,
                list,
                list.stream().map(entry -> new RankProposalStateService.Player(
                        entry.accountId(), false, false
                )).toList()
        );
    }

    private WalletTransferRequest fee(UUID proposalId, UUID accountId) {
        return new WalletTransferRequest(
                accountId, 100, "RANK_ENTRY", proposalId.toString(),
                "rank-fee:" + proposalId + ":" + accountId
        );
    }

    private QueueEntry entry(int seconds, int elo) {
        return QueueEntry.rank(UUID.randomUUID(), Instant.EPOCH.plusSeconds(seconds), elo, 0, 0);
    }
}
