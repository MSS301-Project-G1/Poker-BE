package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AbandonedPlayerServiceImplTest {
    private static final Instant JOINED = Instant.parse("2026-11-11T00:00:00Z");
    private final UUID player = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID partyId = UUID.randomUUID();

    private final QueueStore queueStore = mock(QueueStore.class);
    private final PartyStore partyStore = mock(PartyStore.class);
    private final MatchProposalPlayerRepository players = mock(MatchProposalPlayerRepository.class);
    private final AbandonedPlayerServiceImpl service = new AbandonedPlayerServiceImpl(queueStore, partyStore, players);

    @Test
    void soloPlayerWhoClosedTheTabLeavesTheQueue() {
        when(queueStore.find(player)).thenReturn(Optional.of(QueueEntry.rank(player, JOINED, 1000, 0, 0)));
        when(queueStore.cancel(player, MatchMode.RANK)).thenReturn(QueueStore.CancelResult.CANCELLED);

        assertThat(service.release(player)).isTrue();
        verify(queueStore).cancel(player, MatchMode.RANK);
        verify(partyStore, never()).leave(any());
    }

    @Test
    void partyMemberWhoClosedTheTabStopsThePartySearchAndLeavesTheParty() {
        QueueEntry mine = new QueueEntry(player, MatchMode.NORMAL, JOINED, partyId);
        QueueEntry theirs = new QueueEntry(friend, MatchMode.NORMAL, JOINED, partyId);
        when(partyStore.findByAccountId(player)).thenReturn(Optional.of(party(friend, List.of(friend, player))));
        when(queueStore.find(player)).thenReturn(Optional.of(mine));
        when(queueStore.find(friend)).thenReturn(Optional.of(theirs));
        when(queueStore.cancelParty(anyList())).thenReturn(QueueStore.CancelResult.CANCELLED);
        when(partyStore.leave(player)).thenReturn(PartyStore.LeaveResult.LEFT);

        assertThat(service.release(player)).isTrue();
        verify(queueStore).cancelParty(List.of(theirs, mine));
        verify(partyStore).leave(player);
    }

    @Test
    void partyMemberWhoIsNotSearchingStillLeavesTheParty() {
        when(partyStore.findByAccountId(player)).thenReturn(Optional.of(party(player, List.of(player, friend))));
        when(queueStore.find(player)).thenReturn(Optional.empty());
        when(partyStore.leave(player)).thenReturn(PartyStore.LeaveResult.DISBANDED);

        assertThat(service.release(player)).isTrue();
        verify(partyStore).leave(player);
        verify(queueStore, never()).cancelParty(anyList());
    }

    @Test
    void playerWhoseMatchIsBeingCreatedIsRetriedLater() {
        when(queueStore.find(player)).thenReturn(Optional.of(QueueEntry.rank(player, JOINED, 1000, 0, 0)));
        when(queueStore.cancel(player, MatchMode.RANK)).thenReturn(QueueStore.CancelResult.RESERVED);

        assertThat(service.release(player)).isFalse();
        verify(partyStore, never()).leave(any());
    }

    @Test
    void playerWithAnOpenProposalIsLeftToTheProposalFlow() {
        when(players.existsActiveProposal(player)).thenReturn(true);

        assertThat(service.release(player)).isFalse();
        verifyNoInteractions(queueStore, partyStore);
    }

    @Test
    void playerWithNothingToCleanIsReleased() {
        when(queueStore.find(player)).thenReturn(Optional.empty());
        when(partyStore.findByAccountId(player)).thenReturn(Optional.empty());

        assertThat(service.release(player)).isTrue();
        verify(partyStore, never()).leave(any());
    }

    private PartySnapshot party(UUID leaderId, List<UUID> memberIds) {
        return new PartySnapshot(partyId, "B0162AAA", "421413", leaderId, memberIds, JOINED);
    }
}
