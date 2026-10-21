package com.msspoker.authservice.entity;

import com.msspoker.authservice.enums.CheckinRewardStatus;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_checkins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyCheckin {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "checkin_date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "reward_coins", nullable = false, updatable = false)
    private long rewardCoins;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_status", nullable = false, length = 16)
    private CheckinRewardStatus rewardStatus;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    @Column(name = "rewarded_at")
    private Instant rewardedAt;

    @Column(name = "wallet_transaction_id")
    private UUID walletTransactionId;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
}
