package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.repository.*;
import com.msspoker.competitionservice.service.GameClient;
import com.msspoker.competitionservice.service.RankingClient;
import com.msspoker.competitionservice.service.TournamentScheduler;
import com.msspoker.competitionservice.service.TournamentTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class TournamentApiTest {
    @Autowired WebApplicationContext context;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentEntryRepository entries;
    @Autowired TournamentTableRepository tables;
    @Autowired TournamentSeatRepository seats;
    @Autowired WalletCreditRepository credits;
    @Autowired ItemGrantRepository itemGrants;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @Autowired TournamentScheduler scheduler;
    @MockitoBean GameClient game;
    @MockitoBean RankingClient ranking;
    MockMvc mvc;
    final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        TournamentTestSupport.clean(seats, tables, entries, tournaments, credits, itemGrants, outbox);
        when(game.createTable(any())).thenAnswer(call -> new TableCreatedResponse(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void adminCreatesStartsAndPlayersRegister() throws Exception {
        // Optional slots, rewardCoins and rewardProductId are omitted.
        String body = """
                {"name":"Giải Mở tối thứ Bảy","type":"OPEN","startAt":"%s","entryFee":100}
                """.formatted(Instant.now().plusSeconds(3600));
        mvc.perform(post("/api/admin/tournaments").header("X-User-Id", admin.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMP_FORBIDDEN"));
        String id = json.readTree(mvc.perform(post("/api/admin/tournaments").header("X-User-Id", admin.toString())
                        .header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.slots").value(32))
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/tournaments/{id}/register", id).header("X-User-Id", UUID.randomUUID().toString()))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/tournaments/{id}/register", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tournaments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entryCount").value(4))
                .andExpect(jsonPath("$.content[0].prizePool").value(400))
                .andExpect(jsonPath("$.content[0].type").value("OPEN"));
        mvc.perform(get("/api/tournaments/{id}", id))
                .andExpect(jsonPath("$.prizes[0]").value(200))
                .andExpect(jsonPath("$.prizes[2]").value(80));

        mvc.perform(post("/api/admin/tournaments/{id}/start", id)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/tournaments/{id}/start", id).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.tables[0].round").value("FINAL"))
                .andExpect(jsonPath("$.tables[0].status").value("PLAYING"))
                .andExpect(jsonPath("$.tables[0].seats.length()").value(4));
        mvc.perform(get("/api/tournaments/{id}", id))
                .andExpect(jsonPath("$.entries.length()").value(4));
    }

    @Test
    void invitedPlayersConfirmTheirEliteSeat() throws Exception {
        String tooSoon = """
                {"name":"Cao thủ gấp","type":"ELITE","startAt":"%s","entryFee":0}
                """.formatted(Instant.now().plusSeconds(600));
        mvc.perform(post("/api/admin/tournaments").header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(tooSoon))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMP_INVALID_REQUEST"));
        String body = """
                {"name":"Cao thủ tháng 10","type":"ELITE","startAt":"%s","entryFee":0,"slots":8,
                 "rewardCoins":2000,"rewardProductId":"%s"}
                """.formatted(Instant.now().plusSeconds(3600), UUID.randomUUID());
        UUID id = UUID.fromString(json.readTree(mvc.perform(post("/api/admin/tournaments")
                        .header("X-User-Id", admin.toString()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prizePool").value(2000))
                .andExpect(jsonPath("$.prizes[0]").value(1000))
                .andReturn().getResponse().getContentAsString()).get("id").asString());

        UUID invited = UUID.randomUUID();
        when(ranking.top(anyInt())).thenReturn(TournamentTestSupport.ranked(List.of(invited)));
        TournamentTestSupport.travel(tournaments, entries, id, 35);
        scheduler.runDue();
        mvc.perform(get("/api/tournaments/{id}", id))
                .andExpect(jsonPath("$.entries[0].status").value("INVITED"))
                .andExpect(jsonPath("$.entries[0].confirmBy").isNotEmpty());
        mvc.perform(post("/api/tournaments/{id}/confirm", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tournaments/{id}/confirm", id).header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMP_FORBIDDEN"));
        mvc.perform(post("/api/tournaments/{id}/confirm", id).header("X-User-Id", invited.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.entries[0].confirmBy").doesNotExist());
    }

    @Test
    void invalidTournamentsAreRejected() throws Exception {
        String elitePaid = """
                {"name":"Cao thủ","type":"ELITE","startAt":"%s","entryFee":100}
                """.formatted(Instant.now().plusSeconds(3600));
        String past = """
                {"name":"Cũ","type":"OPEN","startAt":"%s","entryFee":0}
                """.formatted(Instant.now().minusSeconds(60));
        for (String body : new String[]{elitePaid, past, "{\"type\":\"OPEN\"}"}) {
            mvc.perform(post("/api/admin/tournaments").header("X-User-Id", admin.toString())
                            .header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMP_INVALID_REQUEST"));
        }
        mvc.perform(get("/api/tournaments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMP_NOT_FOUND"));
    }
}
