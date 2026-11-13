package com.msspoker.matchmakingservice.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.PartyService;
import com.msspoker.matchmakingservice.service.store.PresenceStore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

@SpringBootTest(
        properties = {
            "matchmaking.finder.enabled=false",
            "matchmaking.game.stub-enabled=true",
            "matchmaking.ranking.stub-enabled=true",
            "matchmaking.restriction.stub-enabled=true",
            "matchmaking.wallet.stub-enabled=true"
        })
@ActiveProfiles("test")
class PresenceWebConfigTest {
    @Autowired WebApplicationContext context;
    @MockitoBean PresenceStore presence;
    @MockitoBean PartyService party;

    @Test
    void thePartyPollMarksThePlayerAsPresentThroughTheRegisteredInterceptor() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        UUID accountId = UUID.randomUUID();
        when(party.current(accountId)).thenReturn(Optional.empty());

        mvc.perform(
                        get("/api/party/me")
                                .header(MatchmakingConstants.USER_ID_HEADER, accountId.toString()))
                .andExpect(status().is2xxSuccessful());

        verify(presence).touch(eq(accountId), any());
    }
}
