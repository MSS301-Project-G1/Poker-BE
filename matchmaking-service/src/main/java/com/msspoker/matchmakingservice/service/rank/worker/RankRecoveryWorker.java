package com.msspoker.matchmakingservice.service.rank.worker;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.service.rank.RankMatchCoordinator;
import com.msspoker.matchmakingservice.service.rank.RankProposalStateService;
import com.msspoker.matchmakingservice.service.rank.RankProposalService;
import com.msspoker.matchmakingservice.service.client.WalletClient;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Tác vụ tự tiếp tục những bước Rank còn dang dở sau lỗi hoặc backend khởi động lại:
 * gọi coordinator cho đề xuất đang tạo bàn, thử hoàn phí còn thiếu qua Wallet,
 * và gọi proposalService.reconcile() để hoàn tất việc đồng bộ Redis.
 * Mỗi bước thành công được ghi nhận để những lần chạy sau biết còn việc gì cần làm.
 */
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

    // Spring tự gọi; mặc định chờ 5 giây sau mỗi lần recover() kết thúc.
    // retryAfter chỉ áp dụng khi chọn đề xuất tạo bàn đang bị treo ở nhánh đầu tiên.
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
