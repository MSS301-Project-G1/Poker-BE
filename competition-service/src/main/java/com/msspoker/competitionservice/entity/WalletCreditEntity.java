package com.msspoker.competitionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pending wallet credit (refunds, later prizes), stored with the business change and sent until accepted. */
@Entity
@Table(name = "wallet_credits")
@Getter
@Setter
public class WalletCreditEntity {
    @Id private UUID id;
    private UUID accountId;
    private long amount;
    private String reason;
    private String refId;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant creditedAt;
    private int attempts;
}
