package com.msspoker.matchmakingservice.service.rank.worker;

import com.msspoker.matchmakingservice.service.rank.RankProposalService;
import com.msspoker.matchmakingservice.service.rank.RankProposalStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Tác vụ tự kiểm tra hạn xác nhận trận Rank, kể cả khi người chơi không thao tác.
 * RankProposalStateService tìm các đề xuất hết hạn; RankProposalService.expire()
 * xử lý trạng thái, hàng chờ và cooldown tương ứng.
 */
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

    // Mặc định chờ 1 giây sau mỗi lần expire() kết thúc; không cần frontend gọi API.
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
