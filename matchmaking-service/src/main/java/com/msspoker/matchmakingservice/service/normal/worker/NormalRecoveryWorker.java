package com.msspoker.matchmakingservice.service.normal.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.PartySnapshot;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.normal.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.store.PartyStore;
import com.msspoker.matchmakingservice.service.proposal.ProposalPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tác vụ phục hồi các đề xuất Normal đã bắt đầu tạo bàn nhưng chưa hoàn tất.
 * Đọc các đề xuất đủ lâu chưa được cập nhật trong PostgreSQL, dựng lại danh sách
 * người chơi và gọi NormalMatchCoordinator.retry(). Dùng lại proposalId để Game
 * nhận diện cùng một yêu cầu khi thử lại, kể cả khi phản hồi lần trước bị mất.
 */
@Component
@ConditionalOnProperty(name = "matchmaking.finder.enabled", havingValue = "true", matchIfMissing = true)
public class NormalRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(NormalRecoveryWorker.class);

    private final ProposalPersistenceService persistence;
    private final NormalMatchCoordinator coordinator;
    private final MatchmakingProperties properties;
    private final PartyStore partyStore;

    public NormalRecoveryWorker(
            ProposalPersistenceService persistence,
            NormalMatchCoordinator coordinator,
            MatchmakingProperties properties,
            PartyStore partyStore
    ) {
        this.persistence = persistence;
        this.coordinator = coordinator;
        this.properties = properties;
        this.partyStore = partyStore;
    }

    // Spring tự gọi; mặc định chờ 5 giây sau mỗi lần recover() kết thúc.
    // retryAfter quyết định đề xuất phải bị treo bao lâu mới được chọn để phục hồi.
    @Scheduled(fixedDelayString = "${matchmaking.recovery.fixed-delay-ms:5000}")
    public void recover() {
        Instant before = Instant.now().minus(properties.getRecovery().getRetryAfter());
        for (MatchProposalEntity proposal : persistence.recoverableNormal(before)) {
            List<QueueEntry> entries = proposal.getPlayers().stream()
                    .map(player -> new QueueEntry(
                            player.getAccountId(),
                            MatchMode.NORMAL,
                            player.getQueueJoinedAt(),
                            partyId(player.getAccountId())
                    ))
                    .toList();
            try {
                coordinator.retry(proposal.getId(), entries);
            } catch (RuntimeException exception) {
                log.error("Could not recover normal proposal proposalId={}", proposal.getId(), exception);
            }
        }
    }

    // Proposal rows do not store the party. A queued party cannot change members,
    // so the account's current party is the one it queued with; complete/requeue need it
    // to clear or refresh the party queue lock.
    private UUID partyId(UUID accountId) {
        return partyStore.findByAccountId(accountId).map(PartySnapshot::partyId).orElse(null);
    }
}
