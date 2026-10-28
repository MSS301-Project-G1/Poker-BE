package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.dto.response.PartyResponse;
import com.msspoker.matchmakingservice.exception.MatchmakingExceptionHandler;
import com.msspoker.matchmakingservice.service.party.PartyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PartyControllerTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PARTY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private PartyService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PartyService.class);
        mvc = standaloneSetup(new PartyController(service))
                .setControllerAdvice(new MatchmakingExceptionHandler())
                .build();
    }

    @Test
    void readsCurrentPartyAfterReload() throws Exception {
        when(service.current(ACCOUNT_ID)).thenReturn(java.util.Optional.of(response()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/party/me")
                        .header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(PARTY_ID.toString()));
    }

    @Test
    void createContract() throws Exception {
        when(service.create(ACCOUNT_ID)).thenReturn(response());

        mvc.perform(post("/api/party/create").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partyId").value(PARTY_ID.toString()))
                .andExpect(jsonPath("$.partyCode").value("AB12CD34"))
                .andExpect(jsonPath("$.password").value("123456"))
                .andExpect(jsonPath("$.leaderId").value(ACCOUNT_ID.toString()));
    }

    @Test
    void joinContract() throws Exception {
        when(service.join(OTHER_ID, "AB12CD34", "123456")).thenReturn(response());

        mvc.perform(post("/api/party/join")
                        .header("X-User-Id", OTHER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyCode\":\"AB12CD34\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyCode").value("AB12CD34"));
    }

    @Test
    void kickContract() throws Exception {
        mvc.perform(post("/api/party/kick")
                        .header("X-User-Id", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + OTHER_ID + "\"}"))
                .andExpect(status().isNoContent());

        verify(service).kick(ACCOUNT_ID, OTHER_ID);
    }

    @Test
    void leaveContractUsesDeleteNamingConvention() throws Exception {
        mvc.perform(delete("/api/party/leave").header("X-User-Id", ACCOUNT_ID))
                .andExpect(status().isNoContent());

        verify(service).leave(ACCOUNT_ID);
    }

    @Test
    void invalidPartyCodeReturnsStandardBadRequest() throws Exception {
        mvc.perform(post("/api/party/join")
                        .header("X-User-Id", OTHER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partyCode\":\"abc\",\"password\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MM_INVALID_REQUEST"));
    }

    @Test
    void missingOrMalformedPasswordReturnsStandardBadRequest() throws Exception {
        for (String body : List.of("{\"partyCode\":\"AB12CD34\"}",
                "{\"partyCode\":\"AB12CD34\",\"password\":\"12ab56\"}")) {
            mvc.perform(post("/api/party/join")
                            .header("X-User-Id", OTHER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MM_INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    private PartyResponse response() {
        return new PartyResponse(PARTY_ID, "AB12CD34", "123456", ACCOUNT_ID, List.of(ACCOUNT_ID), Instant.EPOCH);
    }
}
