package com.msspoker.competitionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pending skin grant (tournament prize), stored with the result and sent to shop-service until accepted. */
@Entity
@Table(name = "item_grants")
@Getter
@Setter
public class ItemGrantEntity {
    @Id private UUID id;
    private UUID accountId;
    private UUID productId;
    private String refId;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant grantedAt;
    private int attempts;
}
