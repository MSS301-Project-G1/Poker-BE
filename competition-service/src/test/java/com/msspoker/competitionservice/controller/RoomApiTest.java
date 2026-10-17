package com.msspoker.competitionservice.controller;

import com.msspoker.competitionservice.dto.response.TableCreatedResponse;
import com.msspoker.competitionservice.repository.CustomRoomRepository;
import com.msspoker.competitionservice.repository.OutboxRepository;
import com.msspoker.competitionservice.repository.RoomMemberRepository;
import com.msspoker.competitionservice.service.GameClient;
import com.msspoker.competitionservice.service.RoomTestSupport;

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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RoomApiTest {
    static final String CREATE = """
            {"visibility":"PUBLIC","password":"secret1","maxPlayers":4,"smallBlind":10,"bigBlind":20}
            """;

    @Autowired WebApplicationContext context;
    @Autowired CustomRoomRepository rooms;
    @Autowired RoomMemberRepository members;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @MockitoBean GameClient game;
    MockMvc mvc;
    final UUID owner = UUID.randomUUID();
    final UUID guest = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        RoomTestSupport.clean(outbox, members, rooms);
    }

    @Test
    void roomLifecycleThroughRestApi() throws Exception {
        String code = json.readTree(mvc.perform(post("/api/rooms").header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.members[0].owner").value(true))
                .andReturn().getResponse().getContentAsString()).get("roomCode").asString();

        mvc.perform(get("/api/rooms")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomCode").value(code))
                .andExpect(jsonPath("$.content[0].playerCount").value(1));
        mvc.perform(post("/api/rooms/{code}/join", code).header("X-User-Id", guest.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMP_WRONG_PASSWORD"));
        mvc.perform(post("/api/rooms/{code}/join", code).header("X-User-Id", guest.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(put("/api/rooms/{code}/ready", code).header("X-User-Id", guest.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ready\":true}"))
                .andExpect(jsonPath("$.members[1].ready").value(true));
        mvc.perform(get("/api/rooms/me").header("X-User-Id", guest.toString()))
                .andExpect(jsonPath("$.roomCode").value(code));

        UUID tableId = UUID.randomUUID();
        when(game.createTable(any())).thenReturn(new TableCreatedResponse(tableId, UUID.randomUUID()));
        mvc.perform(post("/api/rooms/{code}/start", code).header("X-User-Id", owner.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.tableId").value(tableId.toString()));
        mvc.perform(post("/api/rooms/{code}/leave", code).header("X-User-Id", guest.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMP_CONFLICT"));
    }

    @Test
    void ownerManagesMembersAndInvites() throws Exception {
        String code = json.readTree(mvc.perform(post("/api/rooms").header("X-User-Id", owner.toString())
                .contentType(MediaType.APPLICATION_JSON).content(CREATE)).andReturn().getResponse().getContentAsString())
                .get("roomCode").asString();
        mvc.perform(post("/api/rooms/{code}/join", code).header("X-User-Id", guest.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret1\"}"));

        mvc.perform(post("/api/rooms/{code}/invitations", code).header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/rooms/{code}/owner", code).header("X-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"" + guest + "\"}"))
                .andExpect(jsonPath("$.ownerId").value(guest.toString()));
        mvc.perform(delete("/api/rooms/{code}/members/{id}", code, owner).header("X-User-Id", guest.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1));
        mvc.perform(get("/api/rooms/{code}", code).header("X-User-Id", owner.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMP_FORBIDDEN"));
    }

    @Test
    void invalidInputUsesStandardErrors() throws Exception {
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(CREATE)
                        .header("X-Request-Id", "req-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMP_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value("req-1"));
        mvc.perform(post("/api/rooms").header("X-User-Id", owner.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"maxPlayers\":4,\"smallBlind\":20,\"bigBlind\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMP_INVALID_REQUEST"));
        mvc.perform(post("/api/rooms").header("X-User-Id", owner.toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"password\":\"abc\",\"maxPlayers\":4,\"smallBlind\":10,\"bigBlind\":20}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/rooms").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rooms/ABCDEF").header("X-User-Id", owner.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMP_NOT_FOUND"));
    }
}
