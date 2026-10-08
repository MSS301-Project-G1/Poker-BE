package com.msspoker.gameservice.persistence;

import com.msspoker.gameservice.engine.GameMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "match_settings")
@Getter
@Setter
public class MatchSettingsEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) private GameMode mode;
    private long smallBlind;
    private long bigBlind;
    private long startingChips;
    private int turnTimeSeconds;
    private int minPlayers;
    private int maxPlayers;
    private boolean isDeleted;
}
