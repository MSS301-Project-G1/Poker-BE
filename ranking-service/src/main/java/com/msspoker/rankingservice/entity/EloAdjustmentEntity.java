package com.msspoker.rankingservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An admin's change to a player's season Elo; match results are recorded in elo_history instead. */
@Entity
@Table(name = "elo_adjustments")
@Getter
@Setter
public class EloAdjustmentEntity {
    @Id private UUID id;
    private UUID seasonId;
    private UUID accountId;
    private UUID actorId;
    private int eloBefore;
    private int eloAfter;
    private String reason;
    private Instant occurredAt;
}
