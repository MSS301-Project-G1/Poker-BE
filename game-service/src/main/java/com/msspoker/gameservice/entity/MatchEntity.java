package com.msspoker.gameservice.entity;

import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.enums.MatchStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
public class MatchEntity {
    @Id private UUID id;
    private UUID tableId;
    @Enumerated(EnumType.STRING) private GameMode mode;
    private UUID sourceId;
    private long entryFee;
    @Column(columnDefinition = "text") private String requestFingerprint;
    @Enumerated(EnumType.STRING) private MatchStatus status;
    private Instant startedAt;
    private Instant finishedAt;
    private boolean isDeleted;
}
