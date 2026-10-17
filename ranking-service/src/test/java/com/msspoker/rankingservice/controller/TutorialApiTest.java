package com.msspoker.rankingservice.controller;

import com.msspoker.rankingservice.dto.request.WalletCreditRequest;
import com.msspoker.rankingservice.entity.RewardGrantEntity;
import com.msspoker.rankingservice.repository.RewardGrantRepository;
import com.msspoker.rankingservice.repository.TutorialCompletionRepository;
import com.msspoker.rankingservice.service.job.GrantDispatcher;
import com.msspoker.rankingservice.service.TutorialService;
import com.msspoker.rankingservice.service.client.WalletClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class TutorialApiTest {
    @Autowired WebApplicationContext context;
    @Autowired TutorialService tutorials;
    @Autowired TutorialCompletionRepository completions;
    @Autowired RewardGrantRepository grants;
    @Autowired GrantDispatcher dispatcher;
    @Autowired JsonMapper json;
    @MockitoBean WalletClient wallet;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        grants.deleteAll();
        completions.deleteAll();
    }

    @Test
    void completingTheTutorialGrantsTheRewardOnce() throws Exception {
        UUID player = UUID.randomUUID();
        mvc.perform(get("/api/tutorial/me").header("X-User-Id", player.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.rewardCoins").value(200));

        String first = mvc.perform(post("/api/tutorial/complete").header("X-User-Id", player.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.rewardCoins").value(200))
                .andReturn().getResponse().getContentAsString();
        String again = mvc.perform(post("/api/tutorial/complete").header("X-User-Id", player.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(first).get("completedAt"), json.readTree(again).get("completedAt"));
        mvc.perform(get("/api/tutorial/me").header("X-User-Id", player.toString()))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").value(json.readTree(first).get("completedAt").asString()));

        List<RewardGrantEntity> pending = grants.findByAccountIdOrderByCreatedAtAscIdAsc(player);
        assertEquals(1, pending.size());
        assertEquals("tutorial:" + player, pending.getFirst().getIdempotencyKey());
        assertEquals("TUTORIAL_REWARD", pending.getFirst().getReason());
        assertEquals(200, pending.getFirst().getAmount());

        dispatcher.dispatchPending();
        verify(wallet).credit(new WalletCreditRequest(player, 200, "TUTORIAL_REWARD", null, "tutorial:" + player));
        assertNotNull(grants.findAll().getFirst().getGrantedAt());
    }

    @Test
    void concurrentCompletionsGrantOneReward() throws Exception {
        UUID player = UUID.randomUUID();
        int callers = 6;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> complete = () -> {
            start.await();
            return tutorials.complete(player).completed();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<Boolean>> results = IntStream.range(0, callers).mapToObj(i -> pool.submit(complete)).toList();
            start.countDown();
            for (Future<Boolean> result : results) assertTrue(result.get());
        }
        assertEquals(1, completions.count());
        assertEquals(1, grants.findByAccountIdOrderByCreatedAtAscIdAsc(player).size());
    }

    @Test
    void anonymousPlayersAreRejected() throws Exception {
        mvc.perform(post("/api/tutorial/complete"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("RANK_UNAUTHORIZED"));
        mvc.perform(get("/api/tutorial/me")).andExpect(status().isUnauthorized());
        assertEquals(0, completions.count());
    }
}
