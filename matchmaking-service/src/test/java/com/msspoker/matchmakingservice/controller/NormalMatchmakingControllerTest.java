package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.dto.response.NormalReconnectResponse;
import com.msspoker.matchmakingservice.dto.response.QueueStatusResponse;
import com.msspoker.matchmakingservice.enums.MatchmakingError;
import com.msspoker.matchmakingservice.exception.MatchmakingApiException;
import com.msspoker.matchmakingservice.exception.MatchmakingExceptionHandler;
import com.msspoker.matchmakingservice.service.NormalMatchmakingService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NormalMatchmakingControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private NormalMatchmakingService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(NormalMatchmakingService.class);
        mvc = standaloneSetup(new NormalMatchmakingController(service))
                .setControllerAdvice(new MatchmakingExceptionHandler())
                .build();
    }

    @Test
    void joinContract() throws Exception {
        when(service.join(ACCOUNT_ID)).thenReturn(QueueStatusResponse.none());

        mvc.perform(post("/api/matchmaking/normal/join").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void leaveContract() throws Exception {
        mvc.perform(delete("/api/matchmaking/normal/leave").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).leave(ACCOUNT_ID);
    }

    @Test
    void statusContract() throws Exception {
        when(service.status(ACCOUNT_ID)).thenReturn(QueueStatusResponse.none());

        mvc.perform(get("/api/matchmaking/normal/status").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void reconnectContract() throws Exception {
        when(service.reconnect(ACCOUNT_ID)).thenReturn(NormalReconnectResponse.none());

        mvc.perform(get("/api/matchmaking/normal/reconnect").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconnectable").value(false))
                .andExpect(jsonPath("$.status").value("NONE"));
    }

    @Test
    void missingIdentityReturnsStandardUnauthorizedError() throws Exception {
        mvc.perform(get("/api/matchmaking/normal/status").header("X-Request-Id", "request-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MM_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("request-123"));
    }

    @Test
    void businessConflictReturnsStandardError() throws Exception {
        when(service.join(ACCOUNT_ID)).thenThrow(new MatchmakingApiException(
                MatchmakingError.MM_ALREADY_QUEUED,
                HttpStatus.CONFLICT,
                "Tài khoản đã ở trong hàng chờ."
        ));

        mvc.perform(post("/api/matchmaking/normal/join").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MM_ALREADY_QUEUED"))
                .andExpect(jsonPath("$.message").value("Tài khoản đã ở trong hàng chờ."));
    }
}
