package com.msspoker.competitionservice.entity;

import com.msspoker.competitionservice.enums.EntryStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_entries")
@Getter
@Setter
public class TournamentEntryEntity {
    @Id private UUID id;
    private UUID tournamentId;
    private UUID accountId;
    @Enumerated(EnumType.STRING) private EntryStatus status;
    /** Place in the final table; null for players eliminated in a qualifier. */
    private Integer finalPlace;
    private Instant registeredAt;
}
