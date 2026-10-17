package com.msspoker.rankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pending wallet credit, stored with the Elo update and sent until the wallet accepts it. */
@Entity
@Table(name = "rank_reward_credits")
@Getter
@Setter
public class RewardCreditEntity {
    @Id private UUID id;
    private UUID accountId;
    private UUID matchId;
    private int place;
    private long amount;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant creditedAt;
    private int attempts;
}
