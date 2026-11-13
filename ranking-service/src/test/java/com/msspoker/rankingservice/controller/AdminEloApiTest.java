package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloAdjustmentRepository;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.AdminAccountService;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.client.AuthClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminEloApiTest {
    // Fixed ids keep the season positions and tie-breaks predictable.
    static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-00000000e001");
    static final UUID OTHER_ADMIN = UUID.fromString("00000000-0000-7000-8000-00000000e002");
    static final UUID LEADER = UUID.fromString("00000000-0000-7000-8000-00000000e003");
    static final UUID PLAYER = UUID.fromString("00000000-0000-7000-8000-00000000e004");

    @Autowired WebApplicationContext context;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    @Autowired EloAdjustmentRepository adjustments;
    @Autowired SeasonService seasons;
    @Autowired AdminAccountService adminAccounts;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    @MockitoBean AuthClient auth;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        adjustments.deleteAll();
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
        save(LEADER, 1000);
        save(PLAYER, 400);
        when(auth.adminIds()).thenReturn(List.of(ADMIN, OTHER_ADMIN));
        adminAccounts.refresh();
    }

    @Test
    void adminSetsEloTheTierFollowsAndTheChangeIsRecorded() throws Exception {
        mvc.perform(adjust(PLAYER, "{\"elo\":1300,\"reason\":\"  Lost a match to a server crash  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(PLAYER.toString()))
                .andExpect(jsonPath("$.elo").value(1300))
                .andExpect(jsonPath("$.peakElo").value(1300))
                .andExpect(jsonPath("$.tier").value("CHAMPION"))
                .andExpect(jsonPath("$.position").value(1));
        // Lowering keeps the peak; the leaderboard follows the new value.
        mvc.perform(adjust(PLAYER, "{\"elo\":100,\"reason\":\"Boosting\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peakElo").value(1300))
                .andExpect(jsonPath("$.tier").value("IRON"))
                .andExpect(jsonPath("$.position").value(2));
        assertEquals(0, players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasons.active().getId(), PLAYER)
                .orElseThrow().getMatchesPlayed());

        mvc.perform(get("/api/admin/seasons/players/{accountId}/elo-adjustments", PLAYER).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].eloAfter", contains(100, 1300)))
                .andExpect(jsonPath("$.content[*].eloBefore", contains(1300, 400)))
                .andExpect(jsonPath("$.content[1].reason").value("Lost a match to a server crash"))
                .andExpect(jsonPath("$.content[1].actorId").value(ADMIN.toString()))
                .andExpect(jsonPath("$.content[1].seasonId").value(seasons.active().getId().toString()));
    }

    @Test
    void aPlayerWithoutARecordStartsFromTheStartingElo() throws Exception {
        UUID newcomer = UUID.fromString("00000000-0000-7000-8000-00000000e005");
        mvc.perform(adjust(newcomer, "{\"elo\":250,\"reason\":\"Migrated account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elo").value(250))
                .andExpect(jsonPath("$.tier").value("BRONZE"))
                .andExpect(jsonPath("$.matchesPlayed").value(0));
        assertEquals(0, adjustments.findAll().getFirst().getEloBefore());
    }

    @Test
    void onlyAdminsAdjustPlayersWithAReasonAndARealChange() throws Exception {
        mvc.perform(put("/api/admin/seasons/players/{accountId}/elo", PLAYER).header("X-User-Role", "USER")
                        .header("X-User-Id", PLAYER.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"elo\":500,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RANK_FORBIDDEN"));
        mvc.perform(adjust(OTHER_ADMIN, "{\"elo\":500,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RANK_FORBIDDEN"));
        mvc.perform(adjust(ADMIN, "{\"elo\":500,\"reason\":\"x\"}")).andExpect(status().isForbidden());
        for (String body : List.of("{\"elo\":500,\"reason\":\" \"}", "{\"elo\":-1,\"reason\":\"x\"}",
                "{\"elo\":100001,\"reason\":\"x\"}", "{\"reason\":\"x\"}")) {
            mvc.perform(adjust(PLAYER, body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"));
        }
        mvc.perform(adjust(PLAYER, "{\"elo\":400,\"reason\":\"Same\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RANK_CONFLICT"));
        mvc.perform(get("/api/admin/seasons/players/{accountId}/elo-adjustments", PLAYER).header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
        assertEquals(0, adjustments.count());
        assertEquals(400, players.findBySeasonIdAndAccountIdAndIsDeletedFalse(seasons.active().getId(), PLAYER)
                .orElseThrow().getCurrentElo());
    }

    private MockHttpServletRequestBuilder adjust(UUID accountId, String body) {
        return put("/api/admin/seasons/players/{accountId}/elo", accountId).header("X-User-Role", "ADMIN")
                .header("X-User-Id", ADMIN.toString()).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void save(UUID accountId, int elo) {
        players.save(mapper.player(seasons.active().getId(), accountId, elo, calculator.tier(elo)));
    }
}
