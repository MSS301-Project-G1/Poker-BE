package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.dto.response.GameMatchSettingsResponse;
import com.msspoker.matchmakingservice.dto.response.AccountRestrictionsResponse;
import com.msspoker.matchmakingservice.dto.response.PlayerRankingResponse;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.mapper.QueueStatusMapper;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.RankingClient;
import com.msspoker.matchmakingservice.service.RestrictionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankMatchmakingServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Mock private QueueStore queueStore;
    @Mock private MatchProposalPlayerRepository players;
    @Mock private RankingClient ranking;
    @Mock private GameClient game;
    @Mock private PartyStore parties;
    @Mock private RestrictionClient restrictions;
    private RankMatchmakingServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(restrictions.restrictions(any()))
                .thenReturn(new AccountRestrictionsResponse(false, null, null));
        service = new RankMatchmakingServiceImpl(
                queueStore,
                players,
                ranking,
                game,
                parties,
                Mappers.getMapper(QueueStatusMapper.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                restrictions
        );
    }

    @Test
    void eloZeroCanJoinAndKeepsRankingSnapshot() {
        UUID accountId = UUID.randomUUID();
        when(ranking.ranking(accountId)).thenReturn(new PlayerRankingResponse(accountId, 0, 2, 1));
        when(queueStore.enqueue(any())).thenReturn(true);
        when(game.matchSettings(MatchMode.RANK))
                .thenReturn(new GameMatchSettingsResponse(MatchMode.RANK, 2, 6));
        when(queueStore.position(accountId, MatchMode.RANK)).thenReturn(Optional.of(1L));
        when(queueStore.size(MatchMode.RANK)).thenReturn(1L);

        var response = service.join(accountId);

        verify(queueStore).enqueue(QueueEntry.rank(accountId, NOW, 0, 2, 1));
        assertThat(response.status()).isEqualTo("WAITING");
        assertThat(response.mode()).isEqualTo(MatchMode.RANK);
        assertThat(response.minPlayers()).isEqualTo(2);
        assertThat(response.maxPlayers()).isEqualTo(6);
    }

    @Test
    void partyMemberCannotJoinRank() {
        UUID accountId = UUID.randomUUID();
        when(parties.findByAccountId(accountId)).thenReturn(Optional.of(new PartySnapshot(
                UUID.randomUUID(), "AB12CD34", accountId, List.of(accountId), NOW
        )));

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MODE_NOT_AVAILABLE));

        verify(ranking, never()).ranking(any());
        verify(queueStore, never()).enqueue(any());
    }

    @Test
    void rankRestrictionRejectsJoinBeforeReadingElo() {
        UUID accountId = UUID.randomUUID();
        when(restrictions.restrictions(accountId)).thenReturn(new AccountRestrictionsResponse(
                false, null, NOW.plusSeconds(60)
        ));

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_RANK_RESTRICTED));

        verify(ranking, never()).ranking(accountId);
    }

    @Test
    void rankCooldownRejectsJoinBeforeCallingDependencies() {
        UUID accountId = UUID.randomUUID();
        when(queueStore.rankCooldown(accountId)).thenReturn(Optional.of(NOW.plusSeconds(30)));

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_RANK_RESTRICTED));

        verify(restrictions, never()).restrictions(accountId);
        verify(ranking, never()).ranking(accountId);
    }

    @Test
    void activeProposalCannotJoinAgainEvenWhenRedisEntryIsGone() {
        UUID accountId = UUID.randomUUID();
        when(players.existsActiveProposal(accountId)).thenReturn(true);

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_ACTIVE_PROPOSAL));

        verify(ranking, never()).ranking(any());
    }

    @Test
    void activeGameCannotJoinRank() {
        UUID accountId = UUID.randomUUID();
        when(game.activeTable(accountId)).thenReturn(Optional.of(
                new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID())
        ));

        assertThatThrownBy(() -> service.join(accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_MATCH_IN_PROGRESS));
    }

    @Test
    void leaveRemovesRankEntryAndResetsCountdownBelowAdminMinimum() {
        UUID accountId = UUID.randomUUID();
        QueueEntry entry = QueueEntry.rank(accountId, NOW, 1000, 0, 0);
        when(queueStore.find(accountId)).thenReturn(Optional.of(entry));
        when(queueStore.cancel(accountId, MatchMode.RANK)).thenReturn(QueueStore.CancelResult.CANCELLED);
        when(game.matchSettings(MatchMode.RANK))
                .thenReturn(new GameMatchSettingsResponse(MatchMode.RANK, 4, 6));
        when(queueStore.size(MatchMode.RANK)).thenReturn(3L);

        service.leave(accountId);

        verify(queueStore).cancelCountdown(MatchMode.RANK);
    }

    @Test
    void statusReturnsRankProposalWithAcceptanceDeadline() {
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        Instant deadline = NOW.plusSeconds(10);
        MatchProposalEntity proposal = MatchProposalEntity.rank(proposalId, 100, 900, 1000, deadline, NOW);
        MatchProposalPlayerEntity player = MatchProposalPlayerEntity.rankPending(
                accountId, 950, 0, 0, NOW.minusSeconds(5), NOW
        );
        proposal.addPlayer(player);
        when(players.findFirstByAccountIdAndIsDeletedFalseAndProposal_StatusInAndProposal_IsDeletedFalseOrderByCreatedAtDesc(
                any(), any())).thenReturn(Optional.of(player));

        var response = service.status(accountId);

        assertThat(response.status()).isEqualTo("PROPOSED");
        assertThat(response.proposalId()).isEqualTo(proposalId);
        assertThat(response.acceptDeadline()).isEqualTo(deadline);
        verify(queueStore, never()).find(accountId);
    }

    @Test
    void reconnectReturnsRankGameEndpoint() {
        UUID accountId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        InGameSession session = new InGameSession(
                MatchMode.RANK, proposalId, tableId, matchId, "game.example.com", 443, "/ws/game"
        );
        when(queueStore.inGame(accountId)).thenReturn(Optional.of(session));
        when(game.activeTable(accountId)).thenReturn(Optional.of(new TableCreatedResponse(tableId, matchId)));

        var response = service.reconnect(accountId);

        assertThat(response.reconnectable()).isTrue();
        assertThat(response.mode()).isEqualTo(MatchMode.RANK);
        assertThat(response.host()).isEqualTo("game.example.com");
        assertThat(response.port()).isEqualTo(443);
    }
}
