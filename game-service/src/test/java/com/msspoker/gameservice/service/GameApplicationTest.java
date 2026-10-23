package com.msspoker.gameservice.service;

import com.msspoker.gameservice.dto.request.CreateTableRequest;
import com.msspoker.gameservice.dto.request.TableSettingsRequest;
import com.msspoker.gameservice.dto.request.UpdateMatchSettingsRequest;
import com.msspoker.gameservice.dto.response.PlacementResponse;
import com.msspoker.gameservice.enums.ActionType;
import com.msspoker.gameservice.enums.GameMode;
import com.msspoker.gameservice.enums.Street;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.model.game.Seat;
import com.msspoker.gameservice.repository.MatchRepository;
import com.msspoker.gameservice.repository.OutboxRepository;
import com.msspoker.gameservice.security.TrustedIngressFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
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
        CreateTableRequest request = request();
        long initialEvents = outbox.count();
        var created = tables.create(request);
        assertEquals(7, created.tableId().version());
        assertEquals(7, created.matchId().version());
        assertEquals(created, tables.create(request));
        assertEquals(initialEvents + 1, outbox.count());
        assertThrows(GameApiException.class, () -> tables.create(new CreateTableRequest(request.mode(),
                request.sourceId(), List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0)));
        assertThrows(GameApiException.class, () -> tables.create(new CreateTableRequest(request.mode(),
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
        assertEquals(2000, result.placements().stream().mapToLong(PlacementResponse::finalChips).sum());
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
        var request = new CreateTableRequest(GameMode.CUSTOM, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0);
        var created = tables.create(request);
        try {
            settings.update(GameMode.CUSTOM, new UpdateMatchSettingsRequest(new TableSettingsRequest(5, 10, 500, 10), 2, 6));
            assertEquals(1000, registry.require(created.tableId()).getEngine().getRules().startingChips());
            var next = tables.create(new CreateTableRequest(GameMode.CUSTOM, UUID.randomUUID(),
                    List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0));
            assertEquals(500, registry.require(next.tableId()).getEngine().getRules().startingChips());
        } finally {
            settings.update(GameMode.CUSTOM, new UpdateMatchSettingsRequest(new TableSettingsRequest(defaults.smallBlind(),
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

    @Test
    void internalMatchSettingsExposeCurrentAdminPlayerLimits() throws Exception {
        mvc.perform(get("/internal/match-settings/RANK").servletPath("/internal/match-settings/RANK")
                        .header("X-Game-Service-Key", "test-ingress-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("RANK"))
                .andExpect(jsonPath("$.minPlayers").value(2))
                .andExpect(jsonPath("$.maxPlayers").value(6));
    }

    @Test
    void internalActiveTableReportsSeatedPlayersOnly() throws Exception {
        var request = request();
        var created = tables.create(request);
        UUID seated = request.playerIds().getFirst();
        mvc.perform(get("/internal/players/{id}/active-table", seated)
                        .servletPath("/internal/players/" + seated + "/active-table")
                        .header("X-Game-Service-Key", "test-ingress-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(created.tableId().toString()))
                .andExpect(jsonPath("$.matchId").value(created.matchId().toString()));
        UUID outsider = UUID.randomUUID();
        mvc.perform(get("/internal/players/{id}/active-table", outsider)
                        .servletPath("/internal/players/" + outsider + "/active-table")
                        .header("X-Game-Service-Key", "test-ingress-key"))
                .andExpect(status().isOk()).andExpect(content().string("null"));
    }

    @Test
    void publicMatchHistoryIsPagedAndRequiresGatewayIdentity() throws Exception {
        var request = request();
        var created = tables.create(request);
        UUID player = request.playerIds().getFirst();
        mvc.perform(get("/api/game/matches").servletPath("/api/game/matches")
                        .param("accountId", player.toString()).param("size", "5")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content[0].id").value(created.matchId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("RUNNING"))
                .andExpect(jsonPath("$.content[0].placements.length()").value(2));
        mvc.perform(get("/api/game/matches").servletPath("/api/game/matches")
                        .param("accountId", player.toString())
                        .header("X-Game-Service-Key", "test-ingress-key"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("GAME_UNAUTHORIZED"));
        mvc.perform(get("/api/game/matches").servletPath("/api/game/matches")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", player))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GAME_INVALID_REQUEST"));
        mvc.perform(get("/api/game/matches").servletPath("/api/game/matches")
                        .param("accountId", player.toString()).param("size", "101")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", player))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GAME_INVALID_REQUEST"));
    }

    @Test
    void leavingANormalMatchFreesThePlayerAndHandsTheWinToTheOpponent() throws Exception {
        var request = request();
        var created = tables.create(request);
        UUID leaver = request.playerIds().getFirst();
        mvc.perform(post("/api/game/tables/{id}/leave", created.tableId())
                        .servletPath("/api/game/tables/" + created.tableId() + "/leave")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", leaver))
                .andExpect(status().isNoContent());
        var engine = registry.require(created.tableId()).getEngine();
        assertEquals(Street.FINISHED, engine.getStreet());
        assertEquals(1, engine.getSeats().get(1).getPlace());
        assertTrue(tables.active(leaver).isEmpty());
        assertEquals("FINISHED", persistence.result(created.tableId(), leaver).status());
        mvc.perform(post("/api/game/tables/{id}/leave", created.tableId())
                        .servletPath("/api/game/tables/" + created.tableId() + "/leave")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rankMatchCannotBeLeft() throws Exception {
        var created = tables.create(new CreateTableRequest(GameMode.RANK, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0));
        UUID player = registry.require(created.tableId()).getEngine().getSeats().getFirst().getAccountId();
        mvc.perform(post("/api/game/tables/{id}/leave", created.tableId())
                        .servletPath("/api/game/tables/" + created.tableId() + "/leave")
                        .header("X-Game-Service-Key", "test-ingress-key").header("X-User-Id", player))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GAME_FORBIDDEN"));
        assertEquals(created.tableId(), tables.active(player).orElseThrow().tableId());
    }

    private static CreateTableRequest request() {
        return new CreateTableRequest(GameMode.NORMAL, UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()), null, 0);
    }
}
