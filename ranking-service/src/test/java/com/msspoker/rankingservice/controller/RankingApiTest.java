package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.entity.PlayerEloEntity;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.RatingService;
import com.msspoker.rankingservice.service.SeasonService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static com.msspoker.rankingservice.service.RatingTestSupport.accounts;
import static com.msspoker.rankingservice.service.RatingTestSupport.rank;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RankingApiTest {
    // Fixed ids make the tie-break (account id ascending) predictable.
    static final UUID GOD = UUID.fromString("00000000-0000-7000-8000-000000000001");
    static final UUID GRAND = UUID.fromString("00000000-0000-7000-8000-000000000002");
    static final UUID CHAMP = UUID.fromString("00000000-0000-7000-8000-000000000003");
    static final UUID TIED_LOW_ID = UUID.fromString("00000000-0000-7000-8000-000000000004");
    static final UUID TIED_HIGH_ID = UUID.fromString("00000000-0000-7000-8000-000000000005");

    @Autowired WebApplicationContext context;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    @Autowired SeasonService seasons;
    @Autowired RatingService rating;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
        save(GOD, 1700);
        save(GRAND, 1650);
        save(CHAMP, 1300);
        save(TIED_HIGH_ID, 120);
        save(TIED_LOW_ID, 120);
    }

    @Test
    void internalRankingReturnsContractFieldsAndDefaultsForUnknownAccounts() throws Exception {
        mvc.perform(get("/internal/ranking/{id}", CHAMP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(CHAMP.toString()))
                .andExpect(jsonPath("$.elo").value(1300))
                .andExpect(jsonPath("$.tier").value("CHAMPION"))
                .andExpect(jsonPath("$.winStreak").value(0))
                .andExpect(jsonPath("$.lossStreak").value(0));
        mvc.perform(get("/internal/ranking/{id}", GOD))
                .andExpect(jsonPath("$.tier").value("CARD_GOD"));
        mvc.perform(get("/internal/ranking/{id}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elo").value(0))
                .andExpect(jsonPath("$.tier").value("NEWBIE"));
        mvc.perform(get("/internal/ranking/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"));
    }

    @Test
    void topOrdersByEloThenAccountId() throws Exception {
        mvc.perform(get("/internal/ranking/top").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId").value(contains(GOD.toString(), GRAND.toString(), CHAMP.toString(),
                        TIED_LOW_ID.toString(), TIED_HIGH_ID.toString())))
                .andExpect(jsonPath("$[*].position").value(contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$[*].tier").value(contains("CARD_GOD", "CARD_GOD", "CHAMPION", "IRON", "IRON")));
        mvc.perform(get("/internal/ranking/top").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"));
        mvc.perform(get("/internal/ranking/top").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leaderboardPagesKeepGlobalPositions() throws Exception {
        mvc.perform(get("/api/ranking/leaderboard").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].position").value(contains(3, 4)))
                .andExpect(jsonPath("$.content[*].accountId").value(contains(CHAMP.toString(), TIED_LOW_ID.toString())))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void meUsesGatewayIdentity() throws Exception {
        mvc.perform(get("/api/ranking/me").header("X-User-Id", TIED_HIGH_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(5))
                .andExpect(jsonPath("$.elo").value(120))
                .andExpect(jsonPath("$.tier").value("IRON"))
                .andExpect(jsonPath("$.seasonId").value(seasons.active().getId().toString()));
        mvc.perform(get("/api/ranking/me").header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(nullValue()))
                .andExpect(jsonPath("$.elo").value(0))
                .andExpect(jsonPath("$.tier").value("NEWBIE"));
        mvc.perform(get("/api/ranking/me").header("X-Request-Id", "req-me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RANK_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("req-me"));
    }

    @Test
    void matchRatingReturnsTheCallersAppliedDeltaAndReward() throws Exception {
        List<UUID> placed = accounts(6);
        var event = rank(placed, 100);
        rating.matchFinished(event);

        mvc.perform(get("/api/ranking/me/matches/{matchId}", event.matchId()).header("X-User-Id", placed.getFirst().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(event.matchId().toString()))
                .andExpect(jsonPath("$.place").value(1))
                .andExpect(jsonPath("$.eloDelta").value(20))
                .andExpect(jsonPath("$.eloAfter").value(20))
                .andExpect(jsonPath("$.rewardCoins").value(300));
        // Below top 3 there is no reward row, and the Elo floor makes the applied delta 0.
        mvc.perform(get("/api/ranking/me/matches/{matchId}", event.matchId()).header("X-User-Id", placed.get(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place").value(4))
                .andExpect(jsonPath("$.eloDelta").value(0))
                .andExpect(jsonPath("$.rewardCoins").value(0));
        // Not in the match, or the match is not processed yet.
        mvc.perform(get("/api/ranking/me/matches/{matchId}", event.matchId()).header("X-User-Id", GOD.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RANK_NOT_FOUND"));
        mvc.perform(get("/api/ranking/me/matches/{matchId}", UUID.randomUUID()).header("X-User-Id", placed.getFirst().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ranking/me/matches/not-a-uuid").header("X-User-Id", placed.getFirst().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/ranking/me/matches/{matchId}", event.matchId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentSeasonIsTheSeededActiveSeason() throws Exception {
        mvc.perform(get("/api/seasons/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mùa 1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.resetPercent").value(50))
                .andExpect(jsonPath("$.startAt").value("2026-09-30T17:00:00Z"));
    }

    private void save(UUID accountId, int elo) {
        PlayerEloEntity player = mapper.player(seasons.active().getId(), accountId, elo, calculator.tier(elo));
        players.save(player);
    }
}
