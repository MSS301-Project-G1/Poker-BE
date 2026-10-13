package com.msspoker.rankingservice.entity;

import com.msspoker.rankingservice.enums.RewardType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pending coin credit or item grant, stored with the claim and sent until the owner service accepts it. */
@Entity
@Table(name = "reward_grants")
@Getter
@Setter
public class RewardGrantEntity {
    @Id private UUID id;
    private UUID accountId;
    @Enumerated(EnumType.STRING) private RewardType rewardType;
    private long amount;
    private UUID productId;
    private String reason;
    private String refId;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant grantedAt;
    private int attempts;
}
