package com.msspoker.gameservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "match_players")
@Getter
@Setter
public class MatchPlayerEntity {
    @Id private UUID id;
    private UUID matchId;
    private UUID accountId;
    private int seatIndex;
    private int place;
    private long finalChips;
    private boolean leftEarly;
    private boolean isDeleted;
}
