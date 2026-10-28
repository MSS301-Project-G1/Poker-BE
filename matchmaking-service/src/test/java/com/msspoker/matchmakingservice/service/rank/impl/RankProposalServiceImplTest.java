package com.msspoker.matchmakingservice.service.rank.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.service.store.QueueStore;
import com.msspoker.matchmakingservice.service.rank.RankMatchCoordinator;
import com.msspoker.matchmakingservice.service.rank.RankMatchmakingService;
import com.msspoker.matchmakingservice.service.rank.RankProposalStateService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankProposalServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");
    private final RankProposalStateService state = mock(RankProposalStateService.class);
    private final RankMatchCoordinator coordinator = mock(RankMatchCoordinator.class);
    private final RankMatchmakingService matchmaking = mock(RankMatchmakingService.class);
    private final QueueStore queueStore = mock(QueueStore.class);
    private final MatchmakingProperties properties = new MatchmakingProperties();
    private final RankProposalServiceImpl service = new RankProposalServiceImpl(
            state,
            coordinator,
            matchmaking,
            queueStore,
            properties
    );

    @Test
    void lastAcceptReportsInsufficientBalanceAfterCompensation() {
        UUID proposalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(state.accept(proposalId, accountId))
                .thenReturn(new RankProposalStateService.AcceptResult(true, false));
        when(coordinator.start(proposalId)).thenReturn(RankMatchCoordinator.Result.INSUFFICIENT_BALANCE);

        assertThatThrownBy(() -> service.accept(proposalId, accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_INSUFFICIENT_BALANCE));
    }

    @Test
    void lateAcceptExpiresAndAppliesRedisResolution() {
        UUID proposalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        var resolution = new RankProposalStateService.Resolution(
                proposalId, NOW, List.of(), List.of(), List.of(accountId)
        );
        when(state.accept(proposalId, accountId))
                .thenReturn(new RankProposalStateService.AcceptResult(false, true));
        when(state.expire(proposalId)).thenReturn(resolution);

        assertThatThrownBy(() -> service.accept(proposalId, accountId))
                .isInstanceOfSatisfying(MatchmakingApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(MatchmakingError.MM_PROPOSAL_EXPIRED));

        verify(queueStore).startRankCooldown(accountId, NOW.plusSeconds(120));
        verify(state).redisReconciled(proposalId);
    }
}
