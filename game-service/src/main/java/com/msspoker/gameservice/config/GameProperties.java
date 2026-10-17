package com.msspoker.gameservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "game")
public class GameProperties {
    private long smallBlind = 10;
    private long bigBlind = 20;
    private long startingChips = 1000;
    private int turnTimeSeconds = 20;
    private int minPlayers = 2;
    private int maxPlayers = 6;
    private int tournamentMaxPlayers = 8;
    private long handPauseMillis = 4000;
    private int afkTurns = 3;
    private long tableRetentionSeconds = 300;
    private String[] allowedOrigins = {"http://localhost:5173"};
}
