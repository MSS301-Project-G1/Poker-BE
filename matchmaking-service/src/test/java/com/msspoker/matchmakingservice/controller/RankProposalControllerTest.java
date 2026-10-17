package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.exception.MatchmakingExceptionHandler;
import com.msspoker.matchmakingservice.service.RankProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RankProposalControllerTest {
    private static final UUID PROPOSAL_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RankProposalService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(RankProposalService.class);
        mvc = standaloneSetup(new RankProposalController(service))
                .setControllerAdvice(new MatchmakingExceptionHandler())
                .build();
    }

    @Test
    void acceptContract() throws Exception {
        when(service.accept(PROPOSAL_ID, ACCOUNT_ID)).thenReturn(QueueStatusResponse.none());

        mvc.perform(post("/api/matchmaking/proposals/{id}/accept", PROPOSAL_ID)
                        .header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void declineContract() throws Exception {
        mvc.perform(post("/api/matchmaking/proposals/{id}/decline", PROPOSAL_ID)
                        .header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isNoContent());

        verify(service).decline(PROPOSAL_ID, ACCOUNT_ID);
    }

    @Test
    void missingIdentityIsUnauthorized() throws Exception {
        mvc.perform(post("/api/matchmaking/proposals/{id}/accept", PROPOSAL_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MM_UNAUTHORIZED"));
    }
}
