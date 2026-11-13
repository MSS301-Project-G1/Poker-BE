package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.service.RankMatchCoordinator;
import com.msspoker.matchmakingservice.service.RankMatchmakingService;
import com.msspoker.matchmakingservice.service.RankProposalService;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import com.msspoker.matchmakingservice.service.store.QueueStore;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RankProposalServiceImpl implements RankProposalService {
    private final RankProposalStateService state;
    private final RankMatchCoordinator coordinator;
    private final RankMatchmakingService matchmaking;
    private final QueueStore queueStore;
    private final MatchmakingProperties properties;

    @Override
    public QueueStatusResponse accept(UUID proposalId, UUID accountId) {
        RankProposalStateService.AcceptResult result = state.accept(proposalId, accountId);
        if (result.expired()) {
            apply(state.expire(proposalId));
            throw new MatchmakingApiException(
                    MatchmakingError.MM_PROPOSAL_EXPIRED,
                    HttpStatus.CONFLICT,
                    "Đã hết thời gian chấp nhận trận.");
        }
        if (result.startMatch()) {
            RankMatchCoordinator.Result started = coordinator.start(proposalId);
            if (started == RankMatchCoordinator.Result.INSUFFICIENT_BALANCE) {
                throw new MatchmakingApiException(
                        MatchmakingError.MM_INSUFFICIENT_BALANCE,
                        HttpStatus.PAYMENT_REQUIRED,
                        "Số dư không đủ để tham gia trận Rank.");
            }
            if (started == RankMatchCoordinator.Result.FAILED) {
                throw new MatchmakingApiException(
                        MatchmakingError.MM_DEPENDENCY_UNAVAILABLE,
                        HttpStatus.BAD_GATEWAY,
                        "Game từ chối tạo bàn Rank; phí đã được hoàn và người chơi đã được"
                            + " requeue.");
            }
        }
        return matchmaking.status(accountId);
    }

    @Override
    public void decline(UUID proposalId, UUID accountId) {
        apply(state.decline(proposalId, accountId));
    }

    @Override
    public void expire(UUID proposalId) {
        apply(state.expire(proposalId));
    }

    @Override
    public void reconcile(RankProposalStateService.Resolution resolution) {
        apply(resolution);
    }

    public void apply(RankProposalStateService.Resolution resolution) {
        queueStore.discard(resolution.discard());
        queueStore.requeue(resolution.requeue());
        resolution
                .cooldown()
                .forEach(
                        accountId ->
                                queueStore.startRankCooldown(
                                        accountId,
                                        resolution
                                                .occurredAt()
                                                .plus(properties.getRank().getCooldown())));
        state.redisReconciled(resolution.proposalId());
    }
}
