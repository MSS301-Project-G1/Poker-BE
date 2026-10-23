package com.msspoker.matchmakingservice.entity;

import com.msspoker.matchmakingservice.enums.ProposalPlayerStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_proposal_players")
public class MatchProposalPlayerEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private MatchProposalEntity proposal;
    private UUID accountId;
    private Integer eloSnapshot;
    private Integer winStreakSnapshot;
    private Integer lossStreakSnapshot;
    private Instant queueJoinedAt;
    @Enumerated(EnumType.STRING) private ProposalPlayerStatus status;
    private Instant acceptedAt;
    private boolean feeCharged;
    private boolean feeRefunded;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isDeleted;
    @Version private long version;

    protected MatchProposalPlayerEntity() {
    }

    public static MatchProposalPlayerEntity reserved(UUID accountId, Instant joinedAt, Instant now) {
        MatchProposalPlayerEntity player = new MatchProposalPlayerEntity();
        player.id = UUID.randomUUID();
        player.accountId = accountId;
        player.queueJoinedAt = joinedAt;
        player.status = ProposalPlayerStatus.RESERVED;
        player.createdAt = now;
        player.updatedAt = now;
        return player;
    }

    public static MatchProposalPlayerEntity rankPending(
            UUID accountId,
            int elo,
            int winStreak,
            int lossStreak,
            Instant joinedAt,
            Instant now
    ) {
        MatchProposalPlayerEntity player = new MatchProposalPlayerEntity();
        player.id = UUID.randomUUID();
        player.accountId = accountId;
        player.eloSnapshot = elo;
        player.winStreakSnapshot = winStreak;
        player.lossStreakSnapshot = lossStreak;
        player.queueJoinedAt = joinedAt;
        player.status = ProposalPlayerStatus.PENDING;
        player.createdAt = now;
        player.updatedAt = now;
        return player;
    }

    void attachTo(MatchProposalEntity proposal) {
        this.proposal = proposal;
    }

    void matched() {
        status = ProposalPlayerStatus.MATCHED;
        updatedAt = Instant.now();
    }

    void requeued() {
        status = ProposalPlayerStatus.REQUEUED;
        updatedAt = Instant.now();
    }

    public void accept(Instant now) {
        if (status == ProposalPlayerStatus.PENDING) {
            status = ProposalPlayerStatus.ACCEPTED;
            acceptedAt = now;
            updatedAt = now;
        }
    }

    public void decline(Instant now) {
        status = ProposalPlayerStatus.DECLINED;
        updatedAt = now;
    }

    public void charged(Instant now) {
        feeCharged = true;
        updatedAt = now;
    }

    public void refunded(Instant now) {
        feeRefunded = true;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public MatchProposalEntity getProposal() { return proposal; }
    public UUID getAccountId() { return accountId; }
    public Integer getEloSnapshot() { return eloSnapshot; }
    public Integer getWinStreakSnapshot() { return winStreakSnapshot; }
    public Integer getLossStreakSnapshot() { return lossStreakSnapshot; }
    public Instant getQueueJoinedAt() { return queueJoinedAt; }
    public ProposalPlayerStatus getStatus() { return status; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public boolean isFeeCharged() { return feeCharged; }
    public boolean isFeeRefunded() { return feeRefunded; }
}
