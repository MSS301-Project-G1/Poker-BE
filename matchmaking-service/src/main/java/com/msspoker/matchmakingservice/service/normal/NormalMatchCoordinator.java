package com.msspoker.matchmakingservice.service.normal;

import com.msspoker.matchmakingservice.model.QueueEntry;

import java.util.List;
import java.util.UUID;

public interface NormalMatchCoordinator {
    void start(UUID proposalId, List<QueueEntry> entries);
    void retry(UUID proposalId, List<QueueEntry> entries);
}
