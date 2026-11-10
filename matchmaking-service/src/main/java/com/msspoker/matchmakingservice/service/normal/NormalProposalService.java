package com.msspoker.matchmakingservice.service.normal;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import java.util.UUID;

public interface NormalProposalService {
    boolean handles(UUID proposalId);
    QueueStatusResponse accept(UUID proposalId, UUID accountId);
    void decline(UUID proposalId, UUID accountId);
}
