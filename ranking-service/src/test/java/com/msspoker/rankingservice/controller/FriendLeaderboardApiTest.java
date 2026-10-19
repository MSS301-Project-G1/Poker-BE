package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.exception.RankingExceptions;
import com.msspoker.rankingservice.mapper.RankingMapper;
import com.msspoker.rankingservice.repository.EloHistoryRepository;
import com.msspoker.rankingservice.repository.PlayerEloRepository;
import com.msspoker.rankingservice.repository.RewardCreditRepository;
import com.msspoker.rankingservice.service.EloCalculator;
import com.msspoker.rankingservice.service.SeasonService;
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
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class FriendLeaderboardApiTest {
    // Fixed ids keep the season positions and tie-breaks predictable.
    static final UUID ME = UUID.fromString("00000000-0000-7000-8000-00000000f001");
    static final UUID STRONG = UUID.fromString("00000000-0000-7000-8000-00000000f002");
    static final UUID WEAK = UUID.fromString("00000000-0000-7000-8000-00000000f003");
    static final UUID NEW_FRIEND = UUID.fromString("00000000-0000-7000-8000-00000000f004");
    static final UUID STRANGER = UUID.fromString("00000000-0000-7000-8000-00000000f005");
    static final UUID ZERO = UUID.fromString("00000000-0000-7000-8000-00000000f006");

    @Autowired WebApplicationContext context;
    @Autowired PlayerEloRepository players;
    @Autowired EloHistoryRepository history;
    @Autowired RewardCreditRepository rewards;
    @Autowired SeasonService seasons;
    @Autowired RankingMapper mapper;
    @Autowired EloCalculator calculator;
    @MockitoBean SocialClient social;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        rewards.deleteAll();
        history.deleteAll();
        players.deleteAll();
        save(STRANGER, 2000);
        save(STRONG, 1700);
        save(ME, 400);
        save(WEAK, 90);
        save(ZERO, 0);
    }

    @Test
    void friendsAndTheCallerAreRankedTogether() throws Exception {
        // Duplicates and the caller in the friend list are ignored; NEW_FRIEND has no record this season.
        when(social.friends(ME)).thenReturn(List.of(NEW_FRIEND, WEAK, STRONG, ME, ZERO, STRONG));
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", ME.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].accountId", contains(STRONG.toString(), ME.toString(), WEAK.toString(),
                        ZERO.toString(), NEW_FRIEND.toString())))
                .andExpect(jsonPath("$[*].rank", contains(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$[*].you", contains(false, true, false, false, false)))
                .andExpect(jsonPath("$[0].position").value(2))
                .andExpect(jsonPath("$[0].tier").value("CARD_GOD"))
                .andExpect(jsonPath("$[1].position").value(3))
                .andExpect(jsonPath("$[1].elo").value(400))
                .andExpect(jsonPath("$[3].position").value(5))
                .andExpect(jsonPath("$[4].position").doesNotExist())
                .andExpect(jsonPath("$[4].elo").value(0))
                .andExpect(jsonPath("$[4].tier").value("NEWBIE"))
                .andExpect(jsonPath("$[4].matchesPlayed").value(0));
    }

    @Test
    void playersWithoutFriendsSeeThemselvesAndTheListIsCapped() throws Exception {
        when(social.friends(ME)).thenReturn(List.of());
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", ME.toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].you").value(true))
                .andExpect(jsonPath("$[0].position").value(3));

        when(social.friends(ME)).thenReturn(IntStream.range(0, 250).mapToObj(i -> UUID.randomUUID()).toList());
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", ME.toString()))
                .andExpect(jsonPath("$.length()").value(200))
                .andExpect(jsonPath("$[0].accountId").value(ME.toString()));
    }

    @Test
    void socialOutagesAndAnonymousCallsAreRejected() throws Exception {
        when(social.friends(ME)).thenThrow(RankingExceptions.socialUnavailable());
        mvc.perform(get("/api/ranking/friends").header("X-User-Id", ME.toString()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RANK_DEPENDENCY_UNAVAILABLE"));
        mvc.perform(get("/api/ranking/friends"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RANK_UNAUTHORIZED"));
    }

    private void save(UUID accountId, int elo) {
        players.save(mapper.player(seasons.active().getId(), accountId, elo, calculator.tier(elo)));
    }
}
