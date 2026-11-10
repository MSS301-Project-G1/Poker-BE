package com.msspoker.matchmakingservice.service.rank.impl;

import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.enums.ProposalPlayerStatus;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.repository.MatchProposalPlayerRepository;
import com.msspoker.matchmakingservice.service.rank.RankProposalStateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RankProposalStateServiceImpl implements RankProposalStateService {
    private final MatchProposalRepository proposals;
    private final Clock clock;
    private final MatchProposalPlayerRepository players;

    public RankProposalStateServiceImpl(
            MatchProposalRepository proposals,
            MatchProposalPlayerRepository players,
            Clock clock
    ) {
        this.proposals = proposals;
        this.players = players;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AcceptResult accept(UUID proposalId, UUID accountId) {
        MatchProposalEntity proposal = lockedRank(proposalId);
        MatchProposalPlayerEntity player = member(proposal, accountId);
        Instant now = clock.instant();

        if ((proposal.getStatus() == ProposalStatus.CREATING_TABLE
                && player.getStatus() == ProposalPlayerStatus.ACCEPTED)
                || (proposal.getStatus() == ProposalStatus.READY
                && player.getStatus() == ProposalPlayerStatus.MATCHED)) {
            return new AcceptResult(false, false);
        }
        requireWaiting(proposal);
        if (!now.isBefore(proposal.getAcceptDeadline())) {
            return new AcceptResult(false, true);
        }
        player.accept(now);
        boolean allAccepted = proposal.getPlayers().stream()
                .allMatch(candidate -> candidate.getStatus() == ProposalPlayerStatus.ACCEPTED);
        if (allAccepted) proposal.beginCreatingTable(now);
        return new AcceptResult(allAccepted, false);
    }

    @Override
    @Transactional
    public Resolution decline(UUID proposalId, UUID accountId) {
        MatchProposalEntity proposal = lockedRank(proposalId);
        MatchProposalPlayerEntity player = member(proposal, accountId);
        if (proposal.getStatus() == ProposalStatus.DECLINED
                && player.getStatus() == ProposalPlayerStatus.DECLINED) {
            return resolution(proposal, List.of(accountId));
        }
        requireWaiting(proposal);
        Instant now = clock.instant();
        player.decline(now);
        proposal.declined(now);
        return resolution(proposal, List.of(accountId));
    }

    @Override
    @Transactional
    public Resolution expire(UUID proposalId) {
        MatchProposalEntity proposal = lockedRank(proposalId);
        if (proposal.getStatus() != ProposalStatus.WAITING_ACCEPTANCE) {
            return terminalResolution(proposal);
        }
        List<UUID> timedOut = proposal.getPlayers().stream()
                .filter(player -> player.getStatus() == ProposalPlayerStatus.PENDING)
                .map(MatchProposalPlayerEntity::getAccountId)
                .toList();
        proposal.expired(clock.instant());
        return resolution(proposal, timedOut);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> expiredProposalIds(Instant deadline) {
        return proposals.findTop20ByModeAndStatusAndAcceptDeadlineBeforeOrderByAcceptDeadlineAsc(
                MatchMode.RANK, ProposalStatus.WAITING_ACCEPTANCE, deadline
        ).stream().map(MatchProposalEntity::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Work work(UUID proposalId) {
        MatchProposalEntity proposal = proposals.findById(proposalId)
                .orElseThrow(() -> notFound(proposalId));
        if (proposal.getMode() != MatchMode.RANK || proposal.getStatus() != ProposalStatus.CREATING_TABLE) {
            throw unacceptable();
        }
        List<QueueEntry> entries = proposal.getPlayers().stream().map(this::entry).toList();
        List<Player> players = proposal.getPlayers().stream()
                .map(player -> new Player(player.getAccountId(), player.isFeeCharged(), player.isFeeRefunded()))
                .toList();
        return new Work(proposalId, proposal.getEntryFee(), entries, players);
    }

    @Override
    @Transactional
    public void feeCharged(UUID proposalId, UUID accountId) {
        member(lockedRank(proposalId), accountId).charged(clock.instant());
    }

    @Override
    @Transactional
    public void feeRefunded(UUID proposalId, UUID accountId) {
        member(lockedRank(proposalId), accountId).refunded(clock.instant());
    }

    @Override
    @Transactional
    public void ready(UUID proposalId, UUID tableId, UUID matchId) {
        lockedRank(proposalId).ready(tableId, matchId, clock.instant());
    }

    @Override
    @Transactional
    public void failed(UUID proposalId, String failureCode) {
        lockedRank(proposalId).fail(failureCode, clock.instant());
    }

    @Override
    @Transactional
    public void failed(UUID proposalId, String failureCode, UUID rejectedAccountId) {
        MatchProposalEntity proposal = lockedRank(proposalId);
        proposal.fail(failureCode, clock.instant());
        if (rejectedAccountId != null) member(proposal, rejectedAccountId).decline(clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> pendingRefunds() {
        return players
                .findTop50ByFeeChargedTrueAndFeeRefundedFalseAndIsDeletedFalseAndProposal_StatusInOrderByUpdatedAtAsc(
                        List.of(ProposalStatus.FAILED, ProposalStatus.DECLINED, ProposalStatus.EXPIRED)
                )
                .stream()
                .map(player -> new Refund(
                        player.getProposal().getId(),
                        player.getAccountId(),
                        player.getProposal().getEntryFee()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resolution> pendingRedisReconciliations() {
        return proposals.findTop20ByModeAndStatusInAndRedisReconciledAtIsNullOrderByUpdatedAtAsc(
                MatchMode.RANK,
                List.of(ProposalStatus.DECLINED, ProposalStatus.EXPIRED, ProposalStatus.FAILED)
        ).stream().map(this::terminalResolution).toList();
    }

    @Override
    @Transactional
    public void redisReconciled(UUID proposalId) {
        lockedRank(proposalId).redisReconciled(clock.instant());
    }

    private Resolution resolution(MatchProposalEntity proposal, List<UUID> rejectedIds) {
        List<QueueEntry> discard = proposal.getPlayers().stream().map(this::entry).toList();
        List<QueueEntry> requeue = proposal.getPlayers().stream()
                .filter(player -> !rejectedIds.contains(player.getAccountId()))
                .map(this::entry)
                .toList();
        return new Resolution(
                proposal.getId(),
                proposal.getUpdatedAt(),
                requeue,
                discard,
                List.copyOf(rejectedIds)
        );
    }

    private Resolution terminalResolution(MatchProposalEntity proposal) {
        List<UUID> rejected = switch (proposal.getStatus()) {
            case DECLINED -> proposal.getPlayers().stream()
                    .filter(player -> player.getStatus() == ProposalPlayerStatus.DECLINED)
                    .map(MatchProposalPlayerEntity::getAccountId)
                    .toList();
            case EXPIRED -> proposal.getPlayers().stream()
                    .filter(player -> player.getStatus() == ProposalPlayerStatus.PENDING)
                    .map(MatchProposalPlayerEntity::getAccountId)
                    .toList();
            case FAILED -> proposal.getPlayers().stream()
                    .filter(player -> player.getStatus() == ProposalPlayerStatus.DECLINED)
                    .map(MatchProposalPlayerEntity::getAccountId)
                    .toList();
            default -> List.of();
        };
        return resolution(proposal, rejected);
    }

    private QueueEntry entry(MatchProposalPlayerEntity player) {
        return QueueEntry.rank(
                player.getAccountId(),
                player.getQueueJoinedAt(),
                player.getEloSnapshot(),
                player.getWinStreakSnapshot() == null ? 0 : player.getWinStreakSnapshot(),
                player.getLossStreakSnapshot() == null ? 0 : player.getLossStreakSnapshot()
        );
    }

    private MatchProposalEntity lockedRank(UUID proposalId) {
        MatchProposalEntity proposal = proposals.findLockedById(proposalId)
                .orElseThrow(() -> notFound(proposalId));
        if (proposal.getMode() != MatchMode.RANK) throw unacceptable();
        return proposal;
    }

    private MatchProposalPlayerEntity member(MatchProposalEntity proposal, UUID accountId) {
        return proposal.getPlayers().stream()
                .filter(player -> player.getAccountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new MatchmakingApiException(
                        MatchmakingError.MM_NOT_PROPOSAL_MEMBER,
                        HttpStatus.FORBIDDEN,
                        "Người chơi không thuộc proposal này."
                ));
    }

    private void requireWaiting(MatchProposalEntity proposal) {
        if (proposal.getStatus() == ProposalStatus.EXPIRED) {
            throw new MatchmakingApiException(
                    MatchmakingError.MM_PROPOSAL_EXPIRED, HttpStatus.CONFLICT, "Proposal đã hết hạn."
            );
        }
        if (proposal.getStatus() != ProposalStatus.WAITING_ACCEPTANCE) throw unacceptable();
    }

    private MatchmakingApiException notFound(UUID proposalId) {
        return new MatchmakingApiException(
                MatchmakingError.MM_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy proposal " + proposalId + "."
        );
    }

    private MatchmakingApiException unacceptable() {
        return new MatchmakingApiException(
                MatchmakingError.MM_PROPOSAL_NOT_ACCEPTABLE,
                HttpStatus.CONFLICT,
                "Proposal không còn cho phép accept hoặc decline."
        );
    }
}
