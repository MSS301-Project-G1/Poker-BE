package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.exception.MatchmakingExceptionHandler;
import com.msspoker.matchmakingservice.service.rank.RankMatchmakingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RankMatchmakingControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RankMatchmakingService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RankMatchmakingService.class);
        mvc = standaloneSetup(new RankMatchmakingController(service))
                .setControllerAdvice(new MatchmakingExceptionHandler())
                .build();
    }

    @Test
    void joinContract() throws Exception {
        when(service.join(ACCOUNT_ID)).thenReturn(QueueStatusResponse.none());

        mvc.perform(post("/api/matchmaking/rank/join").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void leaveContract() throws Exception {
        mvc.perform(delete("/api/matchmaking/rank/leave").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).leave(ACCOUNT_ID);
    }

    @Test
    void statusContract() throws Exception {
        when(service.status(ACCOUNT_ID)).thenReturn(QueueStatusResponse.none());

        mvc.perform(get("/api/matchmaking/rank/status").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void missingIdentityReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/matchmaking/rank/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MM_UNAUTHORIZED"));
    }

    @Test
    void reconnectContract() throws Exception {
        when(service.reconnect(ACCOUNT_ID)).thenReturn(NormalReconnectResponse.none(
                com.msspoker.matchmakingservice.enums.MatchMode.RANK
        ));

        mvc.perform(get("/api/matchmaking/rank/reconnect").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnectable").value(false))
                .andExpect(jsonPath("$.mode").value("RANK"));
    }
}
