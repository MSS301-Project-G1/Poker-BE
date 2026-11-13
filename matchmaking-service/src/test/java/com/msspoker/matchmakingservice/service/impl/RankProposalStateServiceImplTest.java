package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.service.ProposalPersistenceService;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "matchmaking.finder.enabled=false",
        "matchmaking.game.stub-enabled=true",
        "matchmaking.ranking.stub-enabled=true",
        "matchmaking.restriction.stub-enabled=true",
        "matchmaking.wallet.stub-enabled=true"
})
@ActiveProfiles("test")
class RankProposalStateServiceImplTest {
    @Autowired ProposalPersistenceService persistence;
    @Autowired RankProposalStateService state;
    @Autowired MatchProposalRepository proposals;
    @Autowired MatchProposalPlayerRepository players;

    @AfterEach
    void clean() {
        players.deleteAll();
        proposals.deleteAll();
    }

    @Test
    void concurrentAcceptOnlyStartsMatchOnce() throws Exception {
        UUID proposalId = UUID.randomUUID();
        QueueEntry first = entry(1, 1000, 2, 0);
        QueueEntry second = entry(2, 1020, 1, 0);
        persistence.createRank(proposalId, List.of(first, second), 100, Instant.now().plusSeconds(60));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firstResult = pool.submit(() -> state.accept(proposalId, first.accountId()));
            var secondResult = pool.submit(() -> state.accept(proposalId, second.accountId()));

            assertThat(List.of(firstResult.get().startMatch(), secondResult.get().startMatch()))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(proposals.findById(proposalId).map(MatchProposalEntity::getStatus))
                    .contains(ProposalStatus.CREATING_TABLE);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void declineRequeuesEveryoneExceptDeclinerAndPreservesSnapshots() {
        UUID proposalId = UUID.randomUUID();
        QueueEntry first = entry(1, 900, 4, 1);
        QueueEntry decliner = entry(2, 950, 2, 3);
        persistence.createRank(proposalId, List.of(first, decliner), 100, Instant.now().plusSeconds(60));

        RankProposalStateService.Resolution result = state.decline(proposalId, decliner.accountId());

        assertThat(result.discard()).containsExactly(first, decliner);
        assertThat(result.requeue()).containsExactly(first);
        assertThat(result.cooldown()).containsExactly(decliner.accountId());
        assertThat(proposals.findById(proposalId).map(MatchProposalEntity::getStatus))
                .contains(ProposalStatus.DECLINED);
    }

    @Test
    void expiryRequeuesAcceptedPlayersAndPenalizesPendingPlayers() {
        UUID proposalId = UUID.randomUUID();
        QueueEntry accepted = entry(1, 900, 1, 0);
        QueueEntry pending = entry(2, 920, 0, 1);
        persistence.createRank(proposalId, List.of(accepted, pending), 100, Instant.now().plusSeconds(60));
        state.accept(proposalId, accepted.accountId());

        RankProposalStateService.Resolution result = state.expire(proposalId);

        assertThat(result.requeue()).containsExactly(accepted);
        assertThat(result.cooldown()).containsExactly(pending.accountId());
        assertThat(proposals.findById(proposalId).map(MatchProposalEntity::getStatus))
                .contains(ProposalStatus.EXPIRED);
    }

    @Test
    void terminalProposalRemainsPendingUntilRedisReconciliationIsMarked() {
        UUID proposalId = UUID.randomUUID();
        QueueEntry waiting = entry(1, 1000, 0, 0);
        QueueEntry decliner = entry(2, 1010, 0, 1);
        persistence.createRank(proposalId, List.of(waiting, decliner), 100, Instant.now().plusSeconds(60));
        state.decline(proposalId, decliner.accountId());

        assertThat(state.pendingRedisReconciliations())
                .singleElement()
                .satisfies(resolution -> {
                    assertThat(resolution.proposalId()).isEqualTo(proposalId);
                    assertThat(resolution.requeue()).containsExactly(waiting);
                    assertThat(resolution.cooldown()).containsExactly(decliner.accountId());
                });

        state.redisReconciled(proposalId);

        assertThat(state.pendingRedisReconciliations()).isEmpty();
    }

    private QueueEntry entry(int seconds, int elo, int wins, int losses) {
        return QueueEntry.rank(UUID.randomUUID(), Instant.EPOCH.plusSeconds(seconds), elo, wins, losses);
    }
}
