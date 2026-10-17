package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.NormalMatchCoordinator;
import com.msspoker.matchmakingservice.service.ProposalPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "matchmaking.finder.enabled", havingValue = "true", matchIfMissing = true)
public class NormalRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(NormalRecoveryWorker.class);

    private final ProposalPersistenceService persistence;
    private final NormalMatchCoordinator coordinator;
    private final MatchmakingProperties properties;

    public NormalRecoveryWorker(
            ProposalPersistenceService persistence,
            NormalMatchCoordinator coordinator,
            MatchmakingProperties properties
    ) {
        this.persistence = persistence;
        this.coordinator = coordinator;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${matchmaking.recovery.fixed-delay-ms:5000}")
    public void recover() {
        Instant before = Instant.now().minus(properties.getRecovery().getRetryAfter());
        for (MatchProposalEntity proposal : persistence.recoverableNormal(before)) {
            List<QueueEntry> entries = proposal.getPlayers().stream()
                    .map(player -> new QueueEntry(
                            player.getAccountId(),
                            MatchMode.NORMAL,
                            player.getQueueJoinedAt()
                    ))
                    .toList();
            try {
                coordinator.retry(proposal.getId(), entries);
            } catch (RuntimeException exception) {
                log.error("Could not recover normal proposal proposalId={}", proposal.getId(), exception);
            }
        }
    }
}
