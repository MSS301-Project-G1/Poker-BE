package com.msspoker.matchmakingservice.service.impl;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.entity.*;
import com.msspoker.matchmakingservice.enums.*;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.*;

@Service
public class NormalProposalServiceImpl implements NormalProposalService {
    private final MatchProposalRepository proposals;
    private final NormalMatchCoordinator coordinator;
    private final NormalMatchmakingService matchmaking;
    private final QueueStore queue;
    private final PartyStore parties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public NormalProposalServiceImpl(MatchProposalRepository proposals, NormalMatchCoordinator coordinator,
            NormalMatchmakingService matchmaking, QueueStore queue, PartyStore parties, Clock clock, PlatformTransactionManager transactions) {
        this.proposals = proposals; this.coordinator = coordinator; this.matchmaking = matchmaking;
        this.queue = queue; this.parties = parties; this.clock = clock; this.tx = new TransactionTemplate(transactions);
    }

    @Override public boolean handles(UUID id) {
        return proposals.findById(id).map(p -> p.getMode() == MatchMode.NORMAL).orElse(false);
    }

    @Override public QueueStatusResponse accept(UUID id, UUID accountId) {
        List<QueueEntry> start = tx.execute(status -> {
            var proposal = locked(id);
            var player = member(proposal, accountId);
            if ((proposal.getStatus() == ProposalStatus.CREATING_TABLE && player.getStatus() == ProposalPlayerStatus.ACCEPTED)
                    || (proposal.getStatus() == ProposalStatus.READY && player.getStatus() == ProposalPlayerStatus.MATCHED)) return List.of();
            requirePending(proposal);
            if (!clock.instant().isBefore(proposal.getAcceptDeadline())) {
                proposal.expired(clock.instant());
                return List.of();
            }
            player.accept(clock.instant());
            if (proposal.getPlayers().stream().allMatch(p -> p.getStatus() == ProposalPlayerStatus.ACCEPTED)) {
                proposal.beginCreatingTable(clock.instant());
                return entries(proposal);
            }
            return List.of();
        });
        reconcile(id);
        if (start != null && !start.isEmpty()) coordinator.retry(id, start);
        return matchmaking.status(accountId);
    }

    @Override public void decline(UUID id, UUID accountId) {
        tx.executeWithoutResult(status -> {
            var proposal = locked(id);
            var player = member(proposal, accountId);
            if (proposal.getStatus() == ProposalStatus.DECLINED && player.getStatus() == ProposalPlayerStatus.DECLINED) return;
            requirePending(proposal);
            player.decline(clock.instant());
            proposal.declined(clock.instant());
        });
        reconcile(id);
    }

    // A declined/expired Normal proposal returns the whole group to the lobby, preserving its party.
    // Persist the outcome first; the scheduler retries Redis cleanup after a crash or transient outage.
    private void reconcile(UUID id) {
        tx.executeWithoutResult(status -> {
            var proposal = locked(id);
            if (!List.of(ProposalStatus.DECLINED, ProposalStatus.EXPIRED).contains(proposal.getStatus()) || proposal.getRedisReconciledAt() != null) return;
            queue.discard(entries(proposal));
            proposal.redisReconciled(clock.instant());
        });
    }

    @Scheduled(fixedDelayString = "${matchmaking.finder.fixed-delay-ms:1000}")
    public void expire() {
        for (var expired : proposals.findTop20ByModeAndStatusAndAcceptDeadlineBeforeOrderByAcceptDeadlineAsc(MatchMode.NORMAL, ProposalStatus.WAITING_ACCEPTANCE, clock.instant())) {
            tx.executeWithoutResult(status -> {
                var proposal = locked(expired.getId());
                if (proposal.getStatus() == ProposalStatus.WAITING_ACCEPTANCE && !clock.instant().isBefore(proposal.getAcceptDeadline())) proposal.expired(clock.instant());
            });
        }
        for (var pending : proposals.findTop20ByModeAndStatusInAndRedisReconciledAtIsNullOrderByUpdatedAtAsc(MatchMode.NORMAL, List.of(ProposalStatus.DECLINED, ProposalStatus.EXPIRED))) reconcile(pending.getId());
    }

    private List<QueueEntry> entries(MatchProposalEntity proposal) {
        return proposal.getPlayers().stream().map(p -> new QueueEntry(p.getAccountId(), MatchMode.NORMAL, p.getQueueJoinedAt(),
                parties.findByAccountId(p.getAccountId()).map(party -> party.partyId()).orElse(null))).toList();
    }
    private MatchProposalEntity locked(UUID id) {
        var proposal = proposals.findLockedById(id).orElseThrow(() -> error(MatchmakingError.MM_NOT_FOUND));
        if (proposal.getMode() != MatchMode.NORMAL) throw error(MatchmakingError.MM_PROPOSAL_NOT_ACCEPTABLE);
        return proposal;
    }
    private MatchProposalPlayerEntity member(MatchProposalEntity p, UUID accountId) {
        return p.getPlayers().stream().filter(player -> player.getAccountId().equals(accountId)).findFirst().orElseThrow(() -> error(MatchmakingError.MM_NOT_PROPOSAL_MEMBER));
    }
    private void requirePending(MatchProposalEntity p) {
        if (p.getStatus() != ProposalStatus.WAITING_ACCEPTANCE) throw error(MatchmakingError.MM_PROPOSAL_NOT_ACCEPTABLE);
    }
    private MatchmakingApiException error(MatchmakingError code) {
        return new MatchmakingApiException(code, code == MatchmakingError.MM_NOT_PROPOSAL_MEMBER ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT, "Không thể phản hồi đề xuất trận này.");
    }
}
