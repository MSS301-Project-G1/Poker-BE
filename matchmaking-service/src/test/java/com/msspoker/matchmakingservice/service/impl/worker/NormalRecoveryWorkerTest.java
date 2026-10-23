package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.PartyStore;
import com.msspoker.matchmakingservice.service.ProposalPersistenceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NormalRecoveryWorkerTest {
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    private final ProposalPersistenceService persistence = mock(ProposalPersistenceService.class);
    private final NormalMatchCoordinator coordinator = mock(NormalMatchCoordinator.class);
    private final PartyStore partyStore = mock(PartyStore.class);
    private final NormalRecoveryWorker worker = new NormalRecoveryWorker(
            persistence,
            coordinator,
            new MatchmakingProperties(),
            partyStore
    );

    @Test
    void retriesWithPartyIdSoCompleteCanReleasePartyLock() {
        UUID proposalId = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID solo = UUID.randomUUID();
        MatchProposalEntity proposal = MatchProposalEntity.normal(proposalId, NOW);
        proposal.addPlayer(MatchProposalPlayerEntity.reserved(leader, Instant.EPOCH, NOW));
        proposal.addPlayer(MatchProposalPlayerEntity.reserved(member, Instant.EPOCH, NOW));
        proposal.addPlayer(MatchProposalPlayerEntity.reserved(solo, Instant.EPOCH.plusSeconds(1), NOW));
        when(persistence.recoverableNormal(any())).thenReturn(List.of(proposal));
        PartySnapshot party = new PartySnapshot(partyId, "AB12CD34", "123456", leader, List.of(leader, member), NOW);
        when(partyStore.findByAccountId(leader)).thenReturn(Optional.of(party));
        when(partyStore.findByAccountId(member)).thenReturn(Optional.of(party));
        when(partyStore.findByAccountId(solo)).thenReturn(Optional.empty());

        worker.recover();

        verify(coordinator).retry(eq(proposalId), eq(List.of(
                new QueueEntry(leader, MatchMode.NORMAL, Instant.EPOCH, partyId),
                new QueueEntry(member, MatchMode.NORMAL, Instant.EPOCH, partyId),
                new QueueEntry(solo, MatchMode.NORMAL, Instant.EPOCH.plusSeconds(1))
        )));
    }
}
