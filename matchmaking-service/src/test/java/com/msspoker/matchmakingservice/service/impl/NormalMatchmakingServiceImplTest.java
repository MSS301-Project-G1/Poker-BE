package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.mapper.QueueStatusMapper;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NormalMatchmakingServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

    @Mock private QueueStore queueStore;
    @Mock private MatchProposalPlayerRepository players;
    @Mock private GameClient game;
    @Mock private PartyStore partyStore;
    private NormalMatchmakingServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(game.matchSettings(MatchMode.NORMAL))
                .thenReturn(new GameMatchSettingsResponse(MatchMode.NORMAL, 2, 6));
        service = new NormalMatchmakingServiceImpl(
                queueStore,
                players,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Mappers.getMapper(QueueStatusMapper.class),
                game,
                partyStore
        );
    }

    @Test
    void joinsNormalQueueWithServerTime() {
        UUID accountId = UUID.randomUUID();
        when(queueStore.find(accountId)).thenReturn(Optional.empty());
        when(queueStore.enqueue(any())).thenReturn(true);
        when(queueStore.position(accountId, MatchMode.NORMAL)).thenReturn(Optional.of(1L));
        when(queueStore.size(MatchMode.NORMAL)).thenReturn(1L);
        when(queueStore.countdownDeadline(MatchMode.NORMAL)).thenReturn(Optional.empty());

        var response = service.join(accountId);

        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.mode()).isEqualTo(MatchMode.NORMAL);
        assertThat(response.joinedAt()).isEqualTo(NOW);
        assertThat(response.position()).isEqualTo(1L);
        assertThat(response.playerCount()).isEqualTo(1L);
        assertThat(response.minPlayers()).isEqualTo(2);
        assertThat(response.maxPlayers()).isEqualTo(6);
        verify(queueStore).enqueue(new QueueEntry(accountId, MatchMode.NORMAL, NOW));
    }

    @Test
    void cannotJoinWhileProposalIsCreatingTable() {
        UUID accountId = UUID.randomUUID();
        when(queueStore.find(accountId)).thenReturn(Optional.empty());
        when(players.existsActiveProposal(accountId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MATCH_IN_PROGRESS));

        verify(queueStore, never()).enqueue(any());
        verifyNoInteractions(game);
    }

    @Test
    void cannotJoinWhileGameStillHasAnActiveTable() {
        UUID accountId = UUID.randomUUID();
        when(queueStore.find(accountId)).thenReturn(Optional.empty());
        when(game.activeTable(accountId)).thenReturn(Optional.of(
                new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID())
        ));

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MATCH_IN_PROGRESS));

        verify(queueStore, never()).enqueue(any());
    }

    @Test
    void partyLeaderEnqueuesEveryMemberAtomically() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        PartySnapshot party = new PartySnapshot(
                partyId, "AB12CD34", "123456", leader, List.of(leader, member), NOW.minusSeconds(60)
        );
        when(partyStore.findByAccountId(leader)).thenReturn(Optional.of(party));
        when(queueStore.enqueueParty(anyList())).thenReturn(true);
        when(queueStore.position(leader, MatchMode.NORMAL)).thenReturn(Optional.of(1L));
        when(queueStore.size(MatchMode.NORMAL)).thenReturn(2L);

        var response = service.join(leader);

        List<QueueEntry> expected = List.of(
                new QueueEntry(leader, MatchMode.NORMAL, NOW, partyId),
                new QueueEntry(member, MatchMode.NORMAL, NOW, partyId)
        );
        verify(queueStore).enqueueParty(expected);
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.playerCount()).isEqualTo(2);
    }

    @Test
    void regularPartyMemberCannotStartSearching() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(partyStore.findByAccountId(member)).thenReturn(Optional.of(new PartySnapshot(
                UUID.randomUUID(), "AB12CD34", "123456", leader, List.of(leader, member), NOW
        )));

        assertThatThrownBy(() -> service.join(member))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_NOT_PARTY_LEADER));

        verify(queueStore, never()).enqueueParty(anyList());
        verify(queueStore, never()).enqueue(any());
    }

    @Test
    void partyLeaderCancelsEveryMemberAtomically() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        QueueEntry leaderEntry = new QueueEntry(leader, MatchMode.NORMAL, NOW, partyId);
        QueueEntry memberEntry = new QueueEntry(member, MatchMode.NORMAL, NOW, partyId);
        when(partyStore.findByAccountId(leader)).thenReturn(Optional.of(new PartySnapshot(
                partyId, "AB12CD34", "123456", leader, List.of(leader, member), NOW
        )));
        when(queueStore.find(leader)).thenReturn(Optional.of(leaderEntry));
        when(queueStore.find(member)).thenReturn(Optional.of(memberEntry));
        when(queueStore.cancelParty(List.of(leaderEntry, memberEntry)))
                .thenReturn(QueueStore.CancelResult.CANCELLED);

        service.leave(leader);

        verify(queueStore).cancelParty(List.of(leaderEntry, memberEntry));
        verify(queueStore).cancelCountdown(MatchMode.NORMAL);
    }

    @Test
    void reconnectsToActiveNormalTable() {
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        InGameSession session = new InGameSession(
                MatchMode.NORMAL, proposalId, tableId, matchId, "poker.example.com", 443, "/ws/game"
        );
        when(queueStore.inGame(accountId)).thenReturn(Optional.of(session));
        when(game.activeTable(accountId)).thenReturn(Optional.of(new TableCreatedResponse(tableId, matchId)));

        var response = service.reconnect(accountId);

        assertThat(response.reconnectable()).isTrue();
        assertThat(response.status()).isEqualTo("IN_GAME");
        assertThat(response.mode()).isEqualTo(MatchMode.NORMAL);
        assertThat(response.proposalId()).isEqualTo(proposalId);
        assertThat(response.tableId()).isEqualTo(tableId);
        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.host()).isEqualTo("poker.example.com");
        assertThat(response.port()).isEqualTo(443);
        assertThat(response.websocketPath()).isEqualTo("/ws/game");
    }

    @Test
    void reconnectReturnsNoneWithoutInGameRedisState() {
        UUID accountId = UUID.randomUUID();
        when(queueStore.inGame(accountId)).thenReturn(Optional.empty());

        var response = service.reconnect(accountId);

        assertThat(response.reconnectable()).isFalse();
        assertThat(response.status()).isEqualTo("NONE");
        assertThat(response.mode()).isEqualTo(MatchMode.NORMAL);
        assertThat(response.tableId()).isNull();
        verifyNoInteractions(game);
    }

    @Test
    void reconnectClearsStaleInGameRedisState() {
        UUID accountId = UUID.randomUUID();
        InGameSession session = new InGameSession(
                MatchMode.NORMAL, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "localhost", 8082, "/ws/game"
        );
        when(queueStore.inGame(accountId)).thenReturn(Optional.of(session));
        when(game.activeTable(accountId)).thenReturn(Optional.empty());

        var response = service.reconnect(accountId);

        assertThat(response.reconnectable()).isFalse();
        verify(queueStore).clearInGame(accountId);
    }

    @Test
    void leaveDeletesRedisEntryAndResetsCountdownBelowMinimum() {
        UUID accountId = UUID.randomUUID();
        QueueEntry entry = new QueueEntry(accountId, MatchMode.NORMAL, NOW);
        when(queueStore.find(accountId)).thenReturn(Optional.of(entry));
        when(queueStore.cancel(accountId, MatchMode.NORMAL)).thenReturn(QueueStore.CancelResult.CANCELLED);
        when(queueStore.size(MatchMode.NORMAL)).thenReturn(1L);

        service.leave(accountId);

        verify(queueStore).cancel(accountId, MatchMode.NORMAL);
        verify(queueStore).cancelCountdown(MatchMode.NORMAL);
    }

    @Test
    void cannotCancelReservedPlayer() {
        UUID accountId = UUID.randomUUID();
        QueueEntry entry = new QueueEntry(accountId, MatchMode.NORMAL, NOW);
        when(queueStore.find(accountId)).thenReturn(Optional.of(entry));
        when(queueStore.cancel(accountId, MatchMode.NORMAL)).thenReturn(QueueStore.CancelResult.RESERVED);

        assertThatThrownBy(() -> service.leave(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MATCH_IN_PROGRESS));
    }

    @Test
    void leaveChecksActiveProposalBeforeRedis() {
        UUID accountId = UUID.randomUUID();
        when(players.existsActiveProposal(accountId)).thenReturn(true);

        assertThatThrownBy(() -> service.leave(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MATCH_IN_PROGRESS));

        verify(queueStore, never()).find(accountId);
        verify(queueStore, never()).cancel(any(), any());
    }

    @Test
    void statusUsesCreatingProposalWhenReservationTtlHasExpired() {
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        MatchProposalEntity proposal = MatchProposalEntity.normal(proposalId, NOW);
        MatchProposalPlayerEntity player = MatchProposalPlayerEntity.reserved(accountId, NOW.minusSeconds(30), NOW);
        proposal.addPlayer(player);
        when(players.findFirstByAccountIdAndIsDeletedFalseAndProposal_StatusAndProposal_IsDeletedFalseOrderByCreatedAtDesc(
                accountId,
                com.msspoker.matchmakingservice.enums.ProposalStatus.CREATING_TABLE
        )).thenReturn(Optional.of(player));

        var response = service.status(accountId);

        assertThat(response.status()).isEqualTo("CREATING_TABLE");
        assertThat(response.proposalId()).isEqualTo(proposalId);
        verify(queueStore, never()).find(accountId);
        verify(queueStore, never()).reservation(accountId);
    }

    @Test
    void statusDoesNotReturnHistoricalReadyOrFailedProposal() {
        UUID accountId = UUID.randomUUID();
        when(players.findFirstByAccountIdAndIsDeletedFalseAndProposal_StatusAndProposal_IsDeletedFalseOrderByCreatedAtDesc(
                accountId,
                com.msspoker.matchmakingservice.enums.ProposalStatus.CREATING_TABLE
        )).thenReturn(Optional.empty());
        when(queueStore.inGame(accountId)).thenReturn(Optional.empty());
        when(queueStore.find(accountId)).thenReturn(Optional.empty());

        var response = service.status(accountId);

        assertThat(response.status()).isEqualTo("NONE");
        assertThat(response.proposalId()).isNull();
    }

    @Test
    void statusReturnsReadyOnlyForGameConfirmedInGameSession() {
        UUID accountId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        InGameSession session = new InGameSession(
                MatchMode.NORMAL, UUID.randomUUID(), tableId, matchId, "localhost", 8082, "/ws/game"
        );
        when(queueStore.inGame(accountId)).thenReturn(Optional.of(session));
        when(game.activeTable(accountId)).thenReturn(Optional.of(new TableCreatedResponse(tableId, matchId)));

        var response = service.status(accountId);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.tableId()).isEqualTo(tableId);
        assertThat(response.matchId()).isEqualTo(matchId);
        verify(queueStore, never()).find(accountId);
    }
}
