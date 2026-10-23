package com.msspoker.competitionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Player of one tournament table; seatIndex is the CreateTable player order, place comes from match.finished. */
@Entity
@Table(name = "tournament_seats")
@Getter
@Setter
public class TournamentSeatEntity {
    @Id private UUID id;
    private UUID tournamentTableId;
    private UUID accountId;
    private int seatIndex;
    private Integer place;
}
