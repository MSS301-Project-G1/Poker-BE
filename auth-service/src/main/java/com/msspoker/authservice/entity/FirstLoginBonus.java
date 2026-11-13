package com.msspoker.authservice.entity;

import com.msspoker.authservice.enums.FirstLoginBonusStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "first_login_bonuses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirstLoginBonus {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "reward_coins", nullable = false, updatable = false)
    private long rewardCoins;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false, length = 16)
    private FirstLoginBonusStatus rewardStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rewarded_at")
    private Instant rewardedAt;

    @Column(name = "wallet_transaction_id")
    private UUID walletTransactionId;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
}
