package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.config.MatchmakingProperties;
import com.msspoker.matchmakingservice.dto.request.CreateTableRequest;
import com.msspoker.matchmakingservice.dto.request.WalletTransferRequest;
import com.msspoker.matchmakingservice.dto.response.TableCreatedResponse;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.exception.GameCallRejectedException;
import com.msspoker.matchmakingservice.exception.GameCallUnknownException;
import com.msspoker.matchmakingservice.exception.InsufficientBalanceException;
import com.msspoker.matchmakingservice.exception.WalletCallUnknownException;
import com.msspoker.matchmakingservice.model.InGameSession;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.service.GameClient;
import com.msspoker.matchmakingservice.service.QueueStore;
import com.msspoker.matchmakingservice.service.RankMatchCoordinator;
import com.msspoker.matchmakingservice.service.RankProposalStateService;
import com.msspoker.matchmakingservice.service.WalletClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RankMatchCoordinatorImpl implements RankMatchCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RankMatchCoordinatorImpl.class);

    private final RankProposalStateService state;
    private final WalletClient wallet;
    private final GameClient game;
    private final QueueStore queueStore;
    private final MatchmakingProperties properties;
    private final Clock clock;

    public RankMatchCoordinatorImpl(
            RankProposalStateService state,
            WalletClient wallet,
            GameClient game,
            QueueStore queueStore,
            MatchmakingProperties properties,
            Clock clock
    ) {
        this.state = state;
        this.wallet = wallet;
        this.game = game;
        this.queueStore = queueStore;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Result start(UUID proposalId) {
        RankProposalStateService.Work work = state.work(proposalId);
        List<UUID> charged = new ArrayList<>(work.players().stream()
                .filter(RankProposalStateService.Player::feeCharged)
                .map(RankProposalStateService.Player::accountId)
                .toList());

        for (RankProposalStateService.Player player : work.players()) {
            if (player.feeCharged() || work.entryFee() == 0) continue;
            try {
                wallet.debit(debit(work, player.accountId()));
                state.feeCharged(proposalId, player.accountId());
                charged.add(player.accountId());
            } catch (InsufficientBalanceException exception) {
                failAndRestore(work, charged, player.accountId(), "INSUFFICIENT_BALANCE");
                return Result.INSUFFICIENT_BALANCE;
            } catch (WalletCallUnknownException exception) {
                log.warn("Rank proposal awaits Wallet reconciliation proposalId={} accountId={}",
                        proposalId, player.accountId(), exception);
                return Result.PENDING_RECONCILIATION;
            }
        }

        try {
            TableCreatedResponse response = game.createTable(CreateTableRequest.rank(
                    proposalId,
                    work.entries().stream().map(QueueEntry::accountId).toList(),
                    work.entryFee()
            ));
            queueStore.complete(work.entries(), session(proposalId, response));
            state.ready(proposalId, response.tableId(), response.matchId());
            return Result.READY;
        } catch (GameCallRejectedException exception) {
            failAndRestore(work, charged, null, "GAME_REJECTED");
            return Result.FAILED;
        } catch (GameCallUnknownException exception) {
            log.warn("Rank proposal awaits Game reconciliation proposalId={}", proposalId, exception);
            return Result.PENDING_RECONCILIATION;
        }
    }

    private void failAndRestore(
            RankProposalStateService.Work work,
            List<UUID> charged,
            UUID rejectedAccount,
            String failureCode
    ) {
        state.failed(work.proposalId(), failureCode, rejectedAccount);
        queueStore.discard(work.entries());
        List<QueueEntry> requeue = work.entries().stream()
                .filter(entry -> rejectedAccount == null || !entry.accountId().equals(rejectedAccount))
                .toList();
        queueStore.requeue(requeue);
        if (rejectedAccount != null) {
            queueStore.startRankCooldown(
                    rejectedAccount,
                    clock.instant().plus(properties.getRank().getCooldown())
            );
        }
        state.redisReconciled(work.proposalId());
        charged.forEach(accountId -> refund(work.proposalId(), accountId, work.entryFee()));
    }

    void refund(UUID proposalId, UUID accountId, long amount) {
        if (amount == 0) return;
        try {
            wallet.refund(refundRequest(proposalId, accountId, amount));
            state.feeRefunded(proposalId, accountId);
        } catch (WalletCallUnknownException exception) {
            log.warn("Rank refund remains pending proposalId={} accountId={}", proposalId, accountId, exception);
        }
    }

    private WalletTransferRequest debit(RankProposalStateService.Work work, UUID accountId) {
        return new WalletTransferRequest(
                accountId,
                work.entryFee(),
                "RANK_ENTRY",
                work.proposalId().toString(),
                "rank-fee:" + work.proposalId() + ":" + accountId
        );
    }

    private WalletTransferRequest refundRequest(UUID proposalId, UUID accountId, long amount) {
        return new WalletTransferRequest(
                accountId,
                amount,
                "RANK_REFUND",
                proposalId.toString(),
                "rank-refund:" + proposalId + ":" + accountId
        );
    }

    private InGameSession session(UUID proposalId, TableCreatedResponse response) {
        return new InGameSession(
                MatchMode.RANK,
                proposalId,
                response.tableId(),
                response.matchId(),
                properties.getGame().getPublicHost(),
                properties.getGame().getPublicPort(),
                properties.getGame().getWebsocketPath()
        );
    }
}
