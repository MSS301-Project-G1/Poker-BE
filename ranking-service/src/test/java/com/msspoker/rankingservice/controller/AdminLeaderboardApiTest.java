package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.AdminAccountService;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.client.AuthClient;
import com.msspoker.rankingservice.service.client.SocialClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A Card God limit of 1 shows that an admin above everyone does not take the only Card God place. */
@SpringBootTest(properties = "ranking.card-god-limit=1")
@ActiveProfiles("test")
class AdminLeaderboardApiTest {
    // Fixed ids keep the season positions and tie-breaks predictable.
    static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-00000000a001");
    static final UUID FIRST = UUID.fromString("00000000-0000-7000-8000-00000000a002");
    static final UUID SECOND = UUID.fromString("00000000-0000-7000-8000-00000000a003");
    static final UUID PLAYER = UUID.fromString("00000000-0000-7000-8000-00000000a004");

    @Autowired WebApplicationContext context;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    @Autowired SeasonService seasons;
    @Autowired AdminAccountService adminAccounts;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    @MockitoBean AuthClient auth;
    @MockitoBean SocialClient social;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
        save(ADMIN, 1800);
        save(FIRST, 1700);
        save(SECOND, 1650);
        save(PLAYER, 400);
        when(auth.adminIds()).thenReturn(List.of(ADMIN));
        adminAccounts.refresh();
    }

    @Test
    void leaderboardAndTopLeaveAdminsOut() throws Exception {
        mvc.perform(get("/api/ranking/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].accountId", contains(FIRST.toString(), SECOND.toString(), PLAYER.toString())))
                .andExpect(jsonPath("$.content[*].position", contains(1, 2, 3)))
                .andExpect(jsonPath("$.content[*].tier", contains("CARD_GOD", "GRAND_CHAMPION", "SILVER")))
                .andExpect(jsonPath("$.totalElements").value(3));
        // Elite tournament invitations read this list.
        mvc.perform(get("/internal/ranking/top").param("limit", "32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", contains(FIRST.toString(), SECOND.toString(), PLAYER.toString())));
    }

    @Test
    void adminsKeepTheirEloButHaveNoPositionOrCardGodPlace() throws Exception {
        mvc.perform(get("/api/ranking/me").header("X-User-Id", ADMIN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(nullValue()))
                .andExpect(jsonPath("$.elo").value(1800))
                .andExpect(jsonPath("$.tier").value("GRAND_CHAMPION"));
        mvc.perform(get("/api/ranking/me").header("X-User-Id", FIRST.toString()))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.tier").value("CARD_GOD"));
        mvc.perform(get("/api/ranking/players/{accountId}", ADMIN))
                .andExpect(jsonPath("$.position").value(nullValue()))
                .andExpect(jsonPath("$.elo").value(1800));
        mvc.perform(get("/internal/ranking/{id}", ADMIN))
                .andExpect(jsonPath("$.elo").value(1800))
                .andExpect(jsonPath("$.tier").value("GRAND_CHAMPION"));
        mvc.perform(get("/internal/ranking/{id}", FIRST))
                .andExpect(jsonPath("$.tier").value("CARD_GOD"));
    }

    @Test
    void friendsBoardDropsAdminFriendsButShowsAnAdminCaller() throws Exception {
        when(social.friends(PLAYER)).thenReturn(List.of(ADMIN, FIRST));
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", PLAYER.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", contains(FIRST.toString(), PLAYER.toString())))
                .andExpect(jsonPath("$[*].position", contains(1, 3)));

        when(social.friends(ADMIN)).thenReturn(List.of(PLAYER));
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", ADMIN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", contains(PLAYER.toString(), ADMIN.toString())))
                .andExpect(jsonPath("$[0].position").value(3))
                .andExpect(jsonPath("$[1].position").doesNotExist())
                .andExpect(jsonPath("$[1].elo").value(1800))
                .andExpect(jsonPath("$[1].you").value(true));
    }

    @Test
    void withoutAdminsEveryoneIsRanked() throws Exception {
        when(auth.adminIds()).thenReturn(List.of());
        adminAccounts.refresh();
        mvc.perform(get("/api/ranking/leaderboard"))
                .andExpect(jsonPath("$.content[*].accountId", contains(ADMIN.toString(), FIRST.toString(),
                        SECOND.toString(), PLAYER.toString())))
                .andExpect(jsonPath("$.content[0].tier").value("CARD_GOD"));
        mvc.perform(get("/api/ranking/me").header("X-User-Id", PLAYER.toString()))
                .andExpect(jsonPath("$.position").value(4));
    }

    private void save(UUID accountId, int elo) {
        players.save(mapper.player(seasons.active().getId(), accountId, elo, calculator.tier(elo)));
    }
}
