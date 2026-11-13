package com.msspoker.matchmakingservice.service;

import java.util.UUID;

public interface RankMatchCoordinator {
    Result start(UUID proposalId);

    enum Result {
        READY,
        PENDING_RECONCILIATION,
        INSUFFICIENT_BALANCE,
        FAILED
    }
}
