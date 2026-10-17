package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.service.RankProposalService;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class RankProposalExpiryWorker {
    private static final Logger log = LoggerFactory.getLogger(RankProposalExpiryWorker.class);
    private final RankProposalStateService state;
    private final RankProposalService proposals;
    private final Clock clock;

    public RankProposalExpiryWorker(RankProposalStateService state, RankProposalService proposals, Clock clock) {
        this.state = state;
        this.proposals = proposals;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${matchmaking.proposal.expiry-delay-ms:1000}")
    public void expire() {
        state.expiredProposalIds(clock.instant()).forEach(proposalId -> {
            try {
                proposals.expire(proposalId);
            } catch (RuntimeException exception) {
                log.error("Cannot expire Rank proposal proposalId={}", proposalId, exception);
            }
        });
    }
}
