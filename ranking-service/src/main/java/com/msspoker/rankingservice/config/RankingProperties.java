package com.msspoker.rankingservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** SPEC §8 defaults; every value can be overridden without code changes. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ranking")
public class RankingProperties {
    private int startingElo = 0;
    private int minElo = 0;
    /** Elo change for place 1, 2, ...; places beyond the list use the last value. */
    private List<Integer> eloByPlace = List.of(20, 10, 5, -5, -10, -15);
    /** A finish at or above this place extends the win streak. */
    private int winStreakPlaces = 3;
    /** Coin reward as a multiple of the entry fee for place 1, 2, ... */
    private List<Long> rewardMultipliers = List.of(3L, 2L, 1L);
    /** Minimum Elo for NEWBIE through GRAND_CHAMPION, in Tier order. */
    private List<Integer> tierThresholds = List.of(0, 100, 200, 350, 500, 700, 950, 1250, 1600);
    /** GRAND_CHAMPION players within this leaderboard position are shown as CARD_GOD. */
    private int cardGodLimit = 50;
}
