package com.msspoker.matchmakingservice.controller;

import com.msspoker.matchmakingservice.config.MatchmakingGatewaySecurityConfig;
import com.msspoker.matchmakingservice.exception.MatchmakingExceptionHandler;
import com.msspoker.matchmakingservice.security.MatchmakingGatewayFilter;
import com.msspoker.matchmakingservice.service.NormalMatchmakingService;
import com.msspoker.matchmakingservice.service.NormalProposalService;
import com.msspoker.matchmakingservice.service.PartyService;
import com.msspoker.matchmakingservice.service.RankMatchmakingService;
import com.msspoker.matchmakingservice.service.RankProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MatchmakingGatewaySecurityTest {
    private static final String KEY = "matchmaking-gateway-test-key";
    private final UUID account = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID proposal = UUID.randomUUID();
    private final NormalMatchmakingService normal = mock(NormalMatchmakingService.class);
    private final RankMatchmakingService rank = mock(RankMatchmakingService.class);
    private final NormalProposalService normalProposals = mock(NormalProposalService.class);
    private final RankProposalService rankProposals = mock(RankProposalService.class);
    private final PartyService party = mock(PartyService.class);

    private MockMvc client(String key) {
        var registration = new MatchmakingGatewaySecurityConfig().matchmakingGatewayFilter(key);
        assertThat(registration.getUrlPatterns()).containsExactly("/api", "/api/*");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        return MockMvcBuilders.standaloneSetup(new NormalMatchmakingController(normal), new RankMatchmakingController(rank),
                        new RankProposalController(rankProposals, normalProposals), new PartyController(party))
                .setControllerAdvice(new MatchmakingExceptionHandler())
                .addFilter(registration.getFilter(), registration.getUrlPatterns().toArray(String[]::new)).build();
    }

    private List<MockHttpServletRequestBuilder> requests() {
        return List.of(get("/api/matchmaking/normal/status"), get("/api/matchmaking/normal/reconnect"),
                post("/api/matchmaking/normal/join"), delete("/api/matchmaking/normal/leave"),
                get("/api/matchmaking/rank/status"), get("/api/matchmaking/rank/reconnect"),
                post("/api/matchmaking/rank/join"), delete("/api/matchmaking/rank/leave"),
                post("/api/matchmaking/proposals/" + proposal + "/accept"),
                post("/api/matchmaking/proposals/" + proposal + "/decline"),
                get("/api/party/me"), post("/api/party/create"), post("/api/party/join"),
                post("/api/party/kick"), delete("/api/party/leave"));
    }

    @Test
    void forgedIdentityWithoutValidKeyCannotReachAnyMatchmakingOrPartyOperation() throws Exception {
        var mvc = client(KEY);
        for (String key : List.of("", "wrong")) {
            for (var request : requests()) {
                request.header("X-User-Id", account).header("X-User-Role", "ADMIN");
                if (!key.isEmpty()) request.header(MatchmakingGatewayFilter.HEADER, key);
                mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MM_FORBIDDEN"));
            }
        }
        verifyNoInteractions(normal, rank, normalProposals, rankProposals, party);
    }

    @Test
    void correctKeyPreservesQueueReconnectAndPartyContractsForTheGatewayIdentity() throws Exception {
        var mvc = client(KEY);
        for (String mode : List.of("normal", "rank")) {
            for (String operation : List.of("status", "reconnect")) {
                mvc.perform(get("/api/matchmaking/" + mode + "/" + operation)
                        .header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account)).andExpect(status().isOk());
            }
            mvc.perform(post("/api/matchmaking/" + mode + "/join").header(MatchmakingGatewayFilter.HEADER, KEY)
                    .header("X-User-Id", account)).andExpect(status().isCreated());
            mvc.perform(delete("/api/matchmaking/" + mode + "/leave").header(MatchmakingGatewayFilter.HEADER, KEY)
                    .header("X-User-Id", account)).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/party/me").header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account))
                .andExpect(status().isOk());
        mvc.perform(post("/api/party/create").header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/party/join").header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                .contentType(MediaType.APPLICATION_JSON).content("{\"partyCode\":\"AB12CD34\",\"password\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/party/kick").header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"" + other + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/party/leave").header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account))
                .andExpect(status().isNoContent());
        verify(normal).status(account);
        verify(normal).reconnect(account);
        verify(normal).join(account);
        verify(normal).leave(account);
        verify(rank).status(account);
        verify(rank).reconnect(account);
        verify(rank).join(account);
        verify(rank).leave(account);
        verify(party).current(account);
        verify(party).create(account);
        verify(party).join(account, "AB12CD34", "123456");
        verify(party).kick(account, other);
        verify(party).leave(account);
    }

    @Test
    void correctKeyPreservesBothNormalAndRankProposalDispatch() throws Exception {
        var mvc = client(KEY);
        for (boolean isNormal : List.of(false, true)) {
            when(normalProposals.handles(proposal)).thenReturn(isNormal);
            mvc.perform(post("/api/matchmaking/proposals/" + proposal + "/accept")
                    .header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account)).andExpect(status().isOk());
            mvc.perform(post("/api/matchmaking/proposals/" + proposal + "/decline")
                    .header(MatchmakingGatewayFilter.HEADER, KEY).header("X-User-Id", account)).andExpect(status().isNoContent());
        }
        verify(normalProposals).accept(proposal, account);
        verify(normalProposals).decline(proposal, account);
        verify(rankProposals).accept(proposal, account);
        verify(rankProposals).decline(proposal, account);
    }

    @Test
    void blankKeyDuplicateHeadersAndOtherServiceKeysAreRejected() throws Exception {
        client("").perform(get("/api/matchmaking/normal/status").header(MatchmakingGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account)).andExpect(status().isForbidden());
        var mvc = client(KEY);
        mvc.perform(get("/api/matchmaking/normal/status").header(MatchmakingGatewayFilter.HEADER, KEY, KEY)
                .header("X-User-Id", account)).andExpect(status().isForbidden());
        mvc.perform(get("/api/matchmaking/normal/status").header("X-Wallet-Internal-Key", KEY)
                .header("X-Shop-Gateway-Key", KEY).header("X-User-Id", account)).andExpect(status().isForbidden());
        verifyNoInteractions(normal, rank, normalProposals, rankProposals, party);
    }

    @Test
    void gatewayKeyDoesNotReplaceRequiredAccountIdentity() throws Exception {
        var mvc = client(KEY);
        mvc.perform(get("/api/matchmaking/normal/status").header(MatchmakingGatewayFilter.HEADER, KEY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MM_UNAUTHORIZED"));
        mvc.perform(get("/api/matchmaking/normal/status").header(MatchmakingGatewayFilter.HEADER, KEY)
                .header("X-User-Id", "invalid-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(normal, rank, normalProposals, rankProposals, party);
    }
}
