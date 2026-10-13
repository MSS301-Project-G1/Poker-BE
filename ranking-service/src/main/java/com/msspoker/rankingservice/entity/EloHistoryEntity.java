package com.msspoker.rankingservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elo_history")
@Getter
@Setter
public class EloHistoryEntity {
    @Id private UUID id;
    private UUID accountId;
    private UUID matchId;
    private UUID seasonId;
    private int place;
    private int delta;
    private int eloAfter;
    private Instant createdAt;
}
