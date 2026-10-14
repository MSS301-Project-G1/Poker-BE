package com.msspoker.competitionservice.entity;

import com.msspoker.competitionservice.enums.TableStatus;
import com.msspoker.competitionservice.enums.TournamentRound;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Its id is the CreateTable sourceId, so a retried launch returns the same game table. */
@Entity
@Table(name = "tournament_tables")
@Getter
@Setter
public class TournamentTableEntity {
    @Id private UUID id;
    private UUID tournamentId;
    @Enumerated(EnumType.STRING) private TournamentRound round;
    private int tableIndex;
    @Enumerated(EnumType.STRING) private TableStatus status;
    private UUID tableId;
    private UUID matchId;
    private Instant createdAt;
}
