package com.msspoker.matchmakingservice.service.impl.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.service.RankMatchCoordinator;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import com.msspoker.matchmakingservice.service.RankProposalService;
import com.msspoker.matchmakingservice.service.WalletClient;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class RankRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(RankRecoveryWorker.class);
    private final MatchProposalRepository proposals;
    private final RankMatchCoordinator coordinator;
    private final RankProposalStateService state;
    private final WalletClient wallet;
    private final MatchmakingProperties properties;
    private final RankProposalService proposalService;
    private final Clock clock;

    public RankRecoveryWorker(
            MatchProposalRepository proposals,
            RankMatchCoordinator coordinator,
            RankProposalStateService state,
            WalletClient wallet,
            RankProposalService proposalService,
            MatchmakingProperties properties,
            Clock clock
    ) {
        this.proposals = proposals;
        this.coordinator = coordinator;
        this.state = state;
        this.wallet = wallet;
        this.proposalService = proposalService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${matchmaking.recovery.fixed-delay-ms:5000}")
    public void recover() {
        proposals.findTop20ByModeAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                MatchMode.RANK,
                ProposalStatus.CREATING_TABLE,
                clock.instant().minus(properties.getRecovery().getRetryAfter())
        ).forEach(proposal -> {
            try {
                coordinator.start(proposal.getId());
            } catch (RuntimeException exception) {
                log.error("Cannot recover Rank proposal proposalId={}", proposal.getId(), exception);
            }
        });

        state.pendingRefunds().forEach(refund -> {
            try {
                wallet.refund(new WalletTransferRequest(
                        refund.accountId(),
                        refund.amount(),
                        "RANK_ENTRY_REFUND",
                        refund.proposalId().toString(),
                        "rank-refund:" + refund.proposalId() + ":" + refund.accountId()
                ));
                state.feeRefunded(refund.proposalId(), refund.accountId());
            } catch (RuntimeException exception) {
                log.warn("Rank refund retry failed proposalId={} accountId={}",
                        refund.proposalId(), refund.accountId(), exception);
            }
        });

        state.pendingRedisReconciliations().forEach(resolution -> {
            try {
                proposalService.reconcile(resolution);
            } catch (RuntimeException exception) {
                log.warn("Rank Redis reconciliation failed proposalId={}", resolution.proposalId(), exception);
            }
        });
    }
}
