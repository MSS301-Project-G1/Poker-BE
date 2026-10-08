package com.msspoker.gameservice.service;

import com.msspoker.gameservice.api.GameDtos;
import com.msspoker.gameservice.engine.*;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.persistence.*;
import com.msspoker.gameservice.security.TrustedIngressFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "game.service-key=test-ingress-key")
class GameApplicationTest {
    @Autowired TableService tables;
    @Autowired TableRegistry registry;
    @Autowired MatchPersistenceService persistence;
    @Autowired SettingsService settings;
    @Autowired MatchRepository matches;
    @Autowired OutboxRepository outbox;
    @Autowired JsonMapper json;
    @Autowired WebApplicationContext context;
    @Autowired TrustedIngressFilter ingress;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(ingress).build();
    }

    @Test
    void creationIsIdempotentButRejectsChangedPayloadAndOverlappingRosters() {
        GameDtos.CreateTable request = request();
        long initialEvents = outbox.count();
        var created = tables.create(request);
        assertEquals(7, created.tableId().version());
        assertEquals(7, created.matchId().version());
        assertEquals(created, tables.create(request));
        assertEquals(initialEvents + 1, outbox.count());
        assertThrows(GameApiException.class, () -> tables.create(new GameDtos.CreateTable(request.mode(),
                request.sourceId(), List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0)));
        assertThrows(GameApiException.class, () -> tables.create(new GameDtos.CreateTable(request.mode(),
                UUID.randomUUID(), request.playerIds(), null, 0)));
        assertEquals(created, tables.active(request.playerIds().getFirst()).orElseThrow());
    }

    @Test
    void finishedMatchIsPersistedOnceWithContractEventAndPrivateHistory() {
        var request = request();
        var created = tables.create(request);
        var managed = registry.require(created.tableId());
        var engine = managed.getEngine();
        int guard = 0;
        while (engine.getStreet() != Street.FINISHED) {
            assertTrue(++guard < 1000);
            if (engine.getStreet() == Street.HAND_FINISHED) engine.startHand();
            else {
                Seat actor = engine.getSeats().get(engine.getActorSeat());
                engine.act(actor.getAccountId(), engine.canRaise(actor) ? ActionType.ALL_IN
                        : actor.getStreetBet() < engine.getCurrentBet() ? ActionType.CALL : ActionType.CHECK,
                        engine.getSequence());
            }
        }
        long before = outbox.count();
        persistence.finished(managed);
        persistence.finished(managed);
        assertEquals(before + 1, outbox.count());
        var result = persistence.result(created.tableId(), request.playerIds().getFirst());
        assertEquals("FINISHED", result.status());
        assertEquals(2000, result.placements().stream().mapToLong(GameDtos.Placement::finalChips).sum());
        assertThrows(GameApiException.class, () -> persistence.result(created.tableId(), UUID.randomUUID()));
        var history = persistence.history(request.playerIds().getFirst(), 0, 20);
        assertEquals(1, history.totalElements());
        assertEquals(created.matchId(), history.content().getFirst().id());
        assertEquals(0, persistence.history(UUID.randomUUID(), 0, 20).totalElements());
        var event = outbox.findAll().stream().filter(e -> e.getEventType().equals("match.finished")
                && e.getBody().contains(created.matchId().toString())).findFirst().orElseThrow();
        var body = json.readTree(event.getBody());
        assertEquals(event.getId().toString(), body.get("eventId").asString());
        assertEquals(2, body.get("payload").get("placements").size());
        assertEquals(0, body.get("payload").get("entryFee").asLong());
    }

    @Test
    void adminChangesApplyOnlyToFutureTables() {
        var defaults = settings.list().stream().filter(s -> s.mode() == GameMode.CUSTOM).findFirst().orElseThrow();
        var request = new GameDtos.CreateTable(GameMode.CUSTOM, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0);
        var created = tables.create(request);
        try {
            settings.update(GameMode.CUSTOM, new GameDtos.UpdateSettings(new GameDtos.Settings(5, 10, 500, 10), 2, 6));
            assertEquals(1000, registry.require(created.tableId()).getEngine().getRules().startingChips());
            var next = tables.create(new GameDtos.CreateTable(GameMode.CUSTOM, UUID.randomUUID(),
                    List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0));
            assertEquals(500, registry.require(next.tableId()).getEngine().getRules().startingChips());
        } finally {
            settings.update(GameMode.CUSTOM, new GameDtos.UpdateSettings(new GameDtos.Settings(defaults.smallBlind(),
                    defaults.bigBlind(), defaults.startingChips(), defaults.turnTimeSeconds()), defaults.minPlayers(), defaults.maxPlayers()));
        }
    }

    @Test
    void httpRejectsSpoofedHeadersInvalidBodiesAndNonAdminWrites() throws Exception {
        var request = request();
        mvc.perform(post("/internal/tables").servletPath("/internal/tables")
                .contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/match-settings").servletPath("/api/admin/match-settings")
                .header("X-User-Id", UUID.randomUUID()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/match-settings").servletPath("/api/admin/match-settings")
                .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")).andExpect(status().isForbidden());
        mvc.perform(post("/internal/tables").servletPath("/internal/tables")
                .header("X-Game-Service-Key", "test-ingress-key")
                .contentType("application/json").content("{\"mode\":\"NORMAL\",\"playerIds\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GAME_INVALID_REQUEST"));
        mvc.perform(post("/internal/tables").servletPath("/internal/tables")
                .header("X-Game-Service-Key", "test-ingress-key")
                .contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tableId").exists());
    }

    private static GameDtos.CreateTable request() {
        return new GameDtos.CreateTable(GameMode.NORMAL, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0);
    }
}
