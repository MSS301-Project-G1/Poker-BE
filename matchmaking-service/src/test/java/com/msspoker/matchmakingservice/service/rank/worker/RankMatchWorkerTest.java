package com.msspoker.matchmakingservice.service.rank.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.client.GameClient;
import com.msspoker.matchmakingservice.service.proposal.ProposalPersistenceService;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankMatchWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    private final QueueStore queueStore = mock(QueueStore.class);
    private final ProposalPersistenceService persistence = mock(ProposalPersistenceService.class);
    private final GameClient game = mock(GameClient.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final RankMatchWorker worker = new RankMatchWorker(
            queueStore,
            persistence,
            game,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void fixedEloWindowExcludesPlayersOutsideOneHundredPoints() {
        QueueEntry anchor = rank(1, 1000, 2, 0);
        QueueEntry closeSameForm = rank(2, 1060, 2, 0);
        QueueEntry closeDifferentForm = rank(3, 1040, 5, 0);
        QueueEntry boundary = rank(4, 1100, 0, 0);
        QueueEntry outside = rank(5, 1101, 2, 0);

        List<QueueEntry> selected = worker.select(
                List.of(anchor, outside, closeDifferentForm, boundary, closeSameForm), 6
        );

        assertThat(selected).containsExactly(anchor, closeDifferentForm, closeSameForm, boundary);
        assertThat(selected).doesNotContain(outside);
    }

    @Test
    void adminMinimumStartsCountdownOnlyAfterEnoughCompatiblePlayers() {
        lock(4, 6);
        List<QueueEntry> entries = List.of(
                rank(1, 1000, 0, 0), rank(2, 1010, 0, 0), rank(3, 1020, 0, 0)
        );
        when(queueStore.size(MatchMode.RANK)).thenReturn(3L);
        when(queueStore.oldest(MatchMode.RANK, 3)).thenReturn(entries);

        worker.find();

        verify(queueStore).cancelCountdown(MatchMode.RANK);
        verify(queueStore, never()).startCountdown(any(), any());
        verify(queueStore, never()).reserve(any(), anyList(), any());
    }

    @Test
    void startsThirtySecondFillCountdownAtAdminMinimum() {
        lock(3, 6);
        when(queueStore.countdownDeadline(MatchMode.RANK)).thenReturn(Optional.empty());
        List<QueueEntry> entries = List.of(
                rank(1, 1000, 0, 0), rank(2, 1010, 0, 0), rank(3, 1020, 0, 0)
        );
        when(queueStore.size(MatchMode.RANK)).thenReturn(3L);
        when(queueStore.oldest(MatchMode.RANK, 3)).thenReturn(entries);

        worker.find();

        verify(queueStore).startCountdown(MatchMode.RANK, NOW.plusSeconds(30));
        verify(queueStore, never()).reserve(any(), anyList(), any());
    }

    @Test
    void createsProposalAtDeadlineAndReleasesLockFirst() {
        lock(2, 4);
        List<QueueEntry> entries = List.of(
                rank(1, 1000, 0, 0), rank(2, 1010, 0, 0),
                rank(3, 1020, 0, 0), rank(4, 1030, 0, 0)
        );
        when(queueStore.size(MatchMode.RANK)).thenReturn(4L);
        when(queueStore.oldest(MatchMode.RANK, 4)).thenReturn(entries);
        when(queueStore.reserve(eq(MatchMode.RANK), eq(entries), any(UUID.class))).thenReturn(true);

        worker.find();

        verify(persistence).createRank(any(UUID.class), eq(entries), eq(100L), eq(NOW.plusSeconds(30)));
        InOrder order = inOrder(queueStore, persistence);
        order.verify(queueStore).releaseFinderLock(MatchMode.RANK, "lock");
        order.verify(persistence).createRank(any(UUID.class), eq(entries), eq(100L), eq(NOW.plusSeconds(30)));
    }

    @Test
    void persistenceFailureAtomicallyRequeuesOriginalSnapshots() {
        lock(2, 2);
        List<QueueEntry> entries = List.of(rank(1, 0, 0, 0), rank(2, 10, 0, 0));
        when(queueStore.size(MatchMode.RANK)).thenReturn(2L);
        when(queueStore.oldest(MatchMode.RANK, 2)).thenReturn(entries);
        when(queueStore.reserve(eq(MatchMode.RANK), eq(entries), any(UUID.class))).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable"))
                .when(persistence).createRank(any(), eq(entries), any(Long.class), any());

        worker.find();

        verify(queueStore).requeue(entries);
    }

    @Test
    void fullTableStillWaitsUntilFillDeadline() {
        lock(2, 2);
        var entries = List.of(rank(1, 1000, 0, 0), rank(2, 1010, 0, 0));
        when(queueStore.size(MatchMode.RANK)).thenReturn(2L);
        when(queueStore.oldest(MatchMode.RANK, 2)).thenReturn(entries);
        when(queueStore.countdownDeadline(MatchMode.RANK)).thenReturn(Optional.of(NOW.plusSeconds(20)));
        worker.find();
        verify(queueStore, never()).reserve(any(), anyList(), any());
    }

    private void lock(int minPlayers, int maxPlayers) {
        when(game.matchSettings(MatchMode.RANK))
                .thenReturn(new GameMatchSettingsResponse(MatchMode.RANK, minPlayers, maxPlayers));
        when(queueStore.countdownDeadline(MatchMode.RANK)).thenReturn(Optional.of(NOW));
        when(queueStore.acquireFinderLock(MatchMode.RANK)).thenReturn(Optional.of("lock"));
    }

    private QueueEntry rank(int seconds, int elo, int wins, int losses) {
        return QueueEntry.rank(UUID.randomUUID(), Instant.EPOCH.plusSeconds(seconds), elo, wins, losses);
    }
}
