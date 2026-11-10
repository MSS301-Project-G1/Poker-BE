package com.msspoker.matchmakingservice.service.rank;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;

import java.util.UUID;

public interface RankProposalService {
    QueueStatusResponse accept(UUID proposalId, UUID accountId);
    void decline(UUID proposalId, UUID accountId);
    void expire(UUID proposalId);
    void reconcile(RankProposalStateService.Resolution resolution);
}
