package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.service.GameClient;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NormalMatchWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    private final QueueStore queueStore = mock(QueueStore.class);
    private final NormalMatchCoordinator coordinator = mock(NormalMatchCoordinator.class);
    private final PartyStore partyStore = mock(PartyStore.class);
    private final GameClient game = mock(GameClient.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final NormalMatchWorker worker = new NormalMatchWorker(
            queueStore,
            coordinator,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC),
            partyStore,
            game
    );

    @Test
    void resetsCountdownBelowMinimumPlayers() {
        lock();
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(List.of(entry(1)));

        worker.find();

        verify(queueStore).cancelCountdown(MatchMode.NORMAL);
        verify(queueStore, never()).reserve(any(), anyList(), any());
        verifyNoInteractions(coordinator);
    }

    @Test
    void startsTenSecondCountdownAtMinimumPlayers() {
        lock();
        List<QueueEntry> entries = List.of(entry(1), entry(2));
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(entries);
        when(queueStore.countdownDeadline(MatchMode.NORMAL)).thenReturn(Optional.empty());

        worker.find();

        verify(queueStore).startCountdown(MatchMode.NORMAL, NOW.plusSeconds(10));
        verify(queueStore, never()).reserve(any(), anyList(), any());
        verifyNoInteractions(coordinator);
    }

    @Test
    void waitsWhileCountdownHasNotExpired() {
        lock();
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(List.of(entry(1), entry(2), entry(3)));
        when(queueStore.countdownDeadline(MatchMode.NORMAL)).thenReturn(Optional.of(NOW.plusSeconds(1)));

        worker.find();

        verify(queueStore, never()).reserve(any(), anyList(), any());
        verifyNoInteractions(coordinator);
    }

    @Test
    void startsImmediatelyWhenSixPlayersAreWaiting() {
        lock();
        List<QueueEntry> entries = List.of(entry(1), entry(2), entry(3), entry(4), entry(5), entry(6));
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(entries);
        when(queueStore.reserve(eq(MatchMode.NORMAL), eq(entries), any(UUID.class))).thenReturn(true);

        worker.find();

        verify(queueStore).cancelCountdown(MatchMode.NORMAL);
        verify(coordinator).start(any(UUID.class), eq(entries));
        InOrder order = inOrder(queueStore, coordinator);
        order.verify(queueStore).releaseFinderLock(MatchMode.NORMAL, "lock");
        order.verify(coordinator).start(any(UUID.class), eq(entries));
    }

    @Test
    void startsWithCurrentPlayersWhenCountdownExpires() {
        lock();
        List<QueueEntry> entries = List.of(entry(1), entry(2), entry(3));
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(entries);
        when(queueStore.countdownDeadline(MatchMode.NORMAL)).thenReturn(Optional.of(NOW));
        when(queueStore.reserve(eq(MatchMode.NORMAL), eq(entries), any(UUID.class))).thenReturn(true);

        worker.find();

        verify(queueStore).cancelCountdown(MatchMode.NORMAL);
        verify(coordinator).start(any(UUID.class), eq(entries));
        InOrder order = inOrder(queueStore, coordinator);
        order.verify(queueStore).releaseFinderLock(MatchMode.NORMAL, "lock");
        order.verify(coordinator).start(any(UUID.class), eq(entries));
    }

    @Test
    void keepsPartyTogetherAndFillsRemainingSeatsWithSoloPlayers() {
        lock();
        UUID partyId = UUID.randomUUID();
        QueueEntry partyOne = partyEntry(1, partyId);
        QueueEntry partyTwo = partyEntry(1, partyId);
        QueueEntry partyThree = partyEntry(1, partyId);
        List<QueueEntry> entries = List.of(partyOne, partyTwo, partyThree, entry(2), entry(3), entry(4));
        when(queueStore.size(MatchMode.NORMAL)).thenReturn(6L);
        when(queueStore.oldest(MatchMode.NORMAL, 6)).thenReturn(entries);
        when(partyStore.findByAccountId(partyOne.accountId())).thenReturn(Optional.of(new PartySnapshot(
                partyId,
                "AB12CD34",
                "123456",
                partyOne.accountId(),
                List.of(partyOne.accountId(), partyTwo.accountId(), partyThree.accountId()),
                NOW
        )));
        List.of(partyOne, partyTwo, partyThree)
                .forEach(entry -> when(queueStore.find(entry.accountId())).thenReturn(Optional.of(entry)));
        when(queueStore.reserve(eq(MatchMode.NORMAL), anyList(), any(UUID.class))).thenReturn(true);

        worker.find();

        verify(queueStore).reserve(eq(MatchMode.NORMAL), eq(entries), any(UUID.class));
        verify(coordinator).start(any(UUID.class), eq(entries));
    }

    @Test
    void neverSplitsPartyThatDoesNotFitRemainingSeats() {
        lock();
        QueueEntry soloOne = entry(1);
        QueueEntry soloTwo = entry(2);
        QueueEntry soloThree = entry(3);
        UUID partyId = UUID.randomUUID();
        QueueEntry partyOne = partyEntry(4, partyId);
        QueueEntry partyTwo = partyEntry(4, partyId);
        QueueEntry partyThree = partyEntry(4, partyId);
        QueueEntry partyFour = partyEntry(4, partyId);
        List<QueueEntry> candidates = List.of(soloOne, soloTwo, soloThree,
                partyOne, partyTwo, partyThree, partyFour);
        when(queueStore.size(MatchMode.NORMAL)).thenReturn(7L);
        when(queueStore.oldest(MatchMode.NORMAL, 7)).thenReturn(candidates);
        when(partyStore.findByAccountId(partyOne.accountId())).thenReturn(Optional.of(new PartySnapshot(
                partyId,
                "EF56GH78",
                "123456",
                partyOne.accountId(),
                List.of(partyOne.accountId(), partyTwo.accountId(), partyThree.accountId(), partyFour.accountId()),
                NOW
        )));
        List.of(partyOne, partyTwo, partyThree, partyFour)
                .forEach(entry -> when(queueStore.find(entry.accountId())).thenReturn(Optional.of(entry)));
        when(queueStore.countdownDeadline(MatchMode.NORMAL)).thenReturn(Optional.of(NOW));
        when(queueStore.reserve(eq(MatchMode.NORMAL), anyList(), any(UUID.class))).thenReturn(true);

        worker.find();

        verify(queueStore).reserve(eq(MatchMode.NORMAL), eq(List.of(soloOne, soloTwo, soloThree)), any(UUID.class));
        verify(coordinator).start(any(UUID.class), eq(List.of(soloOne, soloTwo, soloThree)));
    }

    private void lock() {
        when(game.matchSettings(MatchMode.NORMAL))
                .thenReturn(new GameMatchSettingsResponse(MatchMode.NORMAL, 2, 6));
        when(queueStore.acquireFinderLock(MatchMode.NORMAL)).thenReturn(Optional.of("lock"));
    }

    private QueueEntry entry(int seconds) {
        return new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(seconds));
    }

    private QueueEntry partyEntry(int seconds, UUID partyId) {
        return new QueueEntry(UUID.randomUUID(), MatchMode.NORMAL, Instant.EPOCH.plusSeconds(seconds), partyId);
    }
}
