package com.msspoker.matchmakingservice.service.proposal;

import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.model.QueueEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProposalPersistenceService {
    void createNormal(UUID proposalId, List<QueueEntry> entries);
    void createRank(UUID proposalId, List<QueueEntry> entries, long entryFee, Instant acceptDeadline);
    void ready(UUID proposalId, UUID tableId, UUID matchId);
    void failed(UUID proposalId, String failureCode);
    List<MatchProposalEntity> recoverableNormal(Instant updatedBefore);
}
