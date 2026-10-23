package com.msspoker.matchmakingservice.service.proposal.impl;

import com.msspoker.matchmakingservice.entity.MatchProposalEntity;
import com.msspoker.matchmakingservice.entity.MatchProposalPlayerEntity;
import com.msspoker.matchmakingservice.enums.MatchMode;
import com.msspoker.matchmakingservice.enums.ProposalStatus;
import com.msspoker.matchmakingservice.model.QueueEntry;
import com.msspoker.matchmakingservice.repository.MatchProposalRepository;
import com.msspoker.matchmakingservice.service.proposal.ProposalPersistenceService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProposalPersistenceServiceImpl implements ProposalPersistenceService {
    private final MatchProposalRepository proposals;
    private final Clock clock;
    private final com.msspoker.matchmakingservice.config.MatchmakingProperties properties;

    public ProposalPersistenceServiceImpl(MatchProposalRepository proposals, Clock clock, com.msspoker.matchmakingservice.config.MatchmakingProperties properties) {
        this.proposals = proposals;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void createNormal(UUID proposalId, List<QueueEntry> entries) {
        Instant now = clock.instant();
        MatchProposalEntity proposal = MatchProposalEntity.normalPending(proposalId, now, now.plus(properties.getNormal().getAcceptTimeout()));
        entries.forEach(entry -> proposal.addPlayer(
                MatchProposalPlayerEntity.normalPending(entry.accountId(), entry.joinedAt(), now)
        ));
        proposals.save(proposal);
    }

    @Override
    @Transactional
    public void createRank(UUID proposalId, List<QueueEntry> entries, long entryFee, Instant acceptDeadline) {
        if (entries.isEmpty() || entries.stream().anyMatch(entry -> entry.elo() == null)) {
            throw new IllegalArgumentException("Rank proposal cần Elo snapshot cho mọi người chơi");
        }
        Instant now = clock.instant();
        int eloMin = entries.stream().mapToInt(QueueEntry::elo).min().orElseThrow();
        int eloMax = entries.stream().mapToInt(QueueEntry::elo).max().orElseThrow();
        MatchProposalEntity proposal = MatchProposalEntity.rank(
                proposalId, entryFee, eloMin, eloMax, acceptDeadline, now
        );
        entries.forEach(entry -> proposal.addPlayer(
                MatchProposalPlayerEntity.rankPending(
                        entry.accountId(),
                        entry.elo(),
                        entry.winStreak(),
                        entry.lossStreak(),
                        entry.joinedAt(),
                        now
                )
        ));
        proposals.save(proposal);
    }

    @Override
    @Transactional
    public void ready(UUID proposalId, UUID tableId, UUID matchId) {
        proposal(proposalId).ready(tableId, matchId, clock.instant());
    }

    @Override
    @Transactional
    public void failed(UUID proposalId, String failureCode) {
        proposal(proposalId).fail(failureCode, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchProposalEntity> recoverableNormal(Instant updatedBefore) {
        return proposals.findTop20ByModeAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                MatchMode.NORMAL,
                ProposalStatus.CREATING_TABLE,
                updatedBefore
        );
    }

    private MatchProposalEntity proposal(UUID id) {
        return proposals.findById(id).orElseThrow(() -> new EntityNotFoundException("Proposal not found: " + id));
    }
}
