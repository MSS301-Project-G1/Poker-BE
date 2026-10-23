package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.*;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.SeasonTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static com.msspoker.rankingservice.service.SeasonTestSupport.SEED_SEASON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = SeasonTestSupport.DATABASE)
@ActiveProfiles("test")
class SeasonApiTest {
    @Autowired WebApplicationContext context;
    @Autowired SeasonRepository seasons;
    @Autowired SeasonTierRewardRepository tierRewards;
    @Autowired SeasonRewardClaimRepository claims;
    @Autowired RewardGrantRepository grants;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository credits;
    @Autowired OutboxRepository outbox;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    MockMvc mvc;
    final UUID player = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        SeasonTestSupport.reset(seasons, tierRewards, claims, grants, players, history, credits, outbox);
        players.save(mapper.player(SEED_SEASON, player, 520, calculator.tier(520)));
    }

    @Test
    void adminCreatesClosesAndPlayersClaimSeasonRewards() throws Exception {
        String start = Instant.now().plusSeconds(86_400).toString();
        String end = Instant.now().plusSeconds(90 * 86_400L).toString();
        String body = """
                {"name":"Mùa 2","startAt":"%s","endAt":"%s","resetPercent":40,
                 "rewards":[{"tier":"GOLD","rewardType":"COIN","amount":250},
                            {"tier":"DIAMOND","rewardType":"PRODUCT","productId":"%s"}]}
                """.formatted(start, end, UUID.randomUUID());
        mvc.perform(post("/api/admin/seasons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RANK_FORBIDDEN"));
        mvc.perform(post("/api/admin/seasons").header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.rewards.length()").value(2))
                .andExpect(jsonPath("$.rewards[0].tier").value("GOLD"));
        mvc.perform(get("/api/admin/seasons").header("X-User-Role", "ADMIN"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Mùa 2"));
        mvc.perform(get("/api/seasons/{id}/rewards", SEED_SEASON))
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].tier").value("IRON"));

        mvc.perform(post("/api/admin/seasons/close").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(get("/api/seasons/current")).andExpect(jsonPath("$.name").value("Mùa 2"));
        mvc.perform(get("/api/ranking/me").header("X-User-Id", player.toString()))
                .andExpect(jsonPath("$.elo").value(260));

        mvc.perform(get("/api/seasons/rewards/me").header("X-User-Id", player.toString()))
                .andExpect(jsonPath("$[0].seasonId").value(SEED_SEASON.toString()))
                .andExpect(jsonPath("$[0].finalTier").value("GOLD"))
                .andExpect(jsonPath("$[0].claimed").value(false))
                .andExpect(jsonPath("$[0].rewards.length()").value(4));
        mvc.perform(post("/api/seasons/{id}/rewards/claim", SEED_SEASON).header("X-User-Id", player.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true));
        mvc.perform(post("/api/seasons/{id}/rewards/claim", SEED_SEASON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSeasonsAndRewardsAreRejected() throws Exception {
        String now = Instant.now().toString();
        String earlier = Instant.now().minusSeconds(60).toString();
        String[] invalid = {
                """
                {"name":"Ngược","startAt":"%s","endAt":"%s","resetPercent":50}""".formatted(now, earlier),
                """
                {"name":"Phần trăm","startAt":"%s","endAt":"%s","resetPercent":120}""".formatted(earlier, now),
                """
                {"name":"Xu thiếu","startAt":"%s","endAt":"%s","resetPercent":50,
                 "rewards":[{"tier":"GOLD","rewardType":"COIN"}]}""".formatted(earlier, now),
                """
                {"name":"Trùng","startAt":"%s","endAt":"%s","resetPercent":50,
                 "rewards":[{"tier":"GOLD","rewardType":"COIN","amount":1},{"tier":"GOLD","rewardType":"COIN","amount":2}]}""".formatted(earlier, now)};
        for (String body : invalid) {
            mvc.perform(post("/api/admin/seasons").header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"));
        }
        mvc.perform(post("/api/admin/seasons/close").header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RANK_CONFLICT"));
        mvc.perform(get("/api/seasons/{id}/rewards", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
