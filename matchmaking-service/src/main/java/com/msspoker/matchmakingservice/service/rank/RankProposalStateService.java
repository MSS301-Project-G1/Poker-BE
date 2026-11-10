package com.msspoker.matchmakingservice.service.rank;

import com.msspoker.matchmakingservice.model.QueueEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RankProposalStateService {
    AcceptResult accept(UUID proposalId, UUID accountId);
    Resolution decline(UUID proposalId, UUID accountId);
    Resolution expire(UUID proposalId);
    List<UUID> expiredProposalIds(Instant deadline);
    Work work(UUID proposalId);
    void feeCharged(UUID proposalId, UUID accountId);
    void feeRefunded(UUID proposalId, UUID accountId);
    void ready(UUID proposalId, UUID tableId, UUID matchId);
    void failed(UUID proposalId, String failureCode);
    void failed(UUID proposalId, String failureCode, UUID rejectedAccountId);
    List<Refund> pendingRefunds();
    List<Resolution> pendingRedisReconciliations();
    void redisReconciled(UUID proposalId);

    record AcceptResult(boolean startMatch, boolean expired) { }
    record Resolution(
            UUID proposalId,
            Instant occurredAt,
            List<QueueEntry> requeue,
            List<QueueEntry> discard,
            List<UUID> cooldown
    ) { }
    record Player(UUID accountId, boolean feeCharged, boolean feeRefunded) { }
    record Work(UUID proposalId, long entryFee, List<QueueEntry> entries, List<Player> players) { }
    record Refund(UUID proposalId, UUID accountId, long amount) { }
}
