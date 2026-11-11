package com.msspoker.competitionservice.security;

import com.msspoker.competitionservice.config.CompetitionGatewaySecurityConfig;
import com.msspoker.competitionservice.controller.AdminTournamentController;
import com.msspoker.competitionservice.controller.RoomController;
import com.msspoker.competitionservice.controller.TournamentController;
import com.msspoker.competitionservice.enums.RoomVisibility;
import com.msspoker.competitionservice.enums.TournamentType;
import com.msspoker.competitionservice.exception.CompetitionExceptionHandler;
import com.msspoker.competitionservice.service.RoomService;
import com.msspoker.competitionservice.service.TournamentService;
import com.msspoker.competitionservice.validator.AdminAccessValidator;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(CompetitionGatewaySecurityTest.TestConfig.class)
@WebAppConfiguration
@ActiveProfiles("dev")
class CompetitionGatewaySecurityTest {
    private static final String KEY = "competition-gateway-test-key";
    private static final String ROOM = "ROOM01";
    private final UUID account = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID tournament = UUID.randomUUID();

    @Autowired private WebApplicationContext context;
    @Autowired private RoomService rooms;
    @Autowired private TournamentService tournaments;
    private MockMvc mvc;

    private MockMvc client(String key) {
        var registration = new CompetitionGatewaySecurityConfig().competitionGatewayFilter(key);
        assertThat(registration.getUrlPatterns()).containsExactly("/api", "/api/*");
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        // Include component filters so any dev identity override participates in the requests.
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilter(registration.getFilter(), registration.getUrlPatterns().toArray(String[]::new))
                .addFilters(context.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
    }

    @BeforeEach
    void setUp() {
        reset(rooms, tournaments);
        mvc = client(KEY);
    }

    private List<MockHttpServletRequestBuilder> apiRequests() {
        return List.of(get("/api/rooms"), post("/api/rooms"), get("/api/rooms/me"), get("/api/rooms/" + ROOM),
                post("/api/rooms/" + ROOM + "/join"), post("/api/rooms/" + ROOM + "/leave"),
                put("/api/rooms/" + ROOM + "/ready"), delete("/api/rooms/" + ROOM + "/members/" + other),
                put("/api/rooms/" + ROOM + "/owner"), post("/api/rooms/" + ROOM + "/invitations"),
                post("/api/rooms/" + ROOM + "/start"), get("/api/tournaments"), get("/api/tournaments/" + tournament),
                post("/api/tournaments/" + tournament + "/register"), post("/api/tournaments/" + tournament + "/confirm"),
                post("/api/admin/tournaments"), post("/api/admin/tournaments/" + tournament + "/start"),
                post("/api/admin/tournaments/" + tournament + "/cancel"));
    }

    @Test
    void forgedAdminCannotReachAnyRoomOrTournamentEndpointWithoutValidGatewayKey() throws Exception {
        for (String key : List.of("", "wrong")) {
            for (var request : apiRequests()) {
                request.header("X-User-Id", account).header("X-User-Role", "ADMIN").header("X-Dev-User", other);
                if (!key.isEmpty()) request.header(CompetitionGatewayFilter.HEADER, key);
                mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMP_FORBIDDEN"));
            }
        }
        verifyNoInteractions(rooms, tournaments);
    }

    @Test
    void gatewayAllowsAnonymousPublicListsAndTournamentDetailsWithOriginalQueries() throws Exception {
        mvc.perform(get("/api/rooms?page=2&size=7").header(CompetitionGatewayFilter.HEADER, KEY)).andExpect(status().isOk());
        mvc.perform(get("/api/tournaments?past=true&page=1&size=5").header(CompetitionGatewayFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tournaments/" + tournament).header(CompetitionGatewayFilter.HEADER, KEY))
                .andExpect(status().isOk());
        verify(rooms).publicRooms(2, 7);
        verify(tournaments).list(true, 1, 5);
        verify(tournaments).get(tournament);
    }

    @Test
    void gatewayIdentityAndRoomArgumentsReachTheExistingBusinessServices() throws Exception {
        mvc.perform(post("/api/rooms").header(CompetitionGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"visibility":"PUBLIC","maxPlayers":4,"smallBlind":10,"bigBlind":20}
                        """)).andExpect(status().isOk());
        for (String path : List.of("/api/rooms/me", "/api/rooms/" + ROOM)) {
            mvc.perform(get(path).header(CompetitionGatewayFilter.HEADER, KEY).header("X-User-Id", account))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/rooms/" + ROOM + "/join").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret12\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms/" + ROOM + "/leave").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account)).andExpect(status().isOk());
        mvc.perform(put("/api/rooms/" + ROOM + "/ready").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).contentType(MediaType.APPLICATION_JSON).content("{\"ready\":true}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/rooms/" + ROOM + "/members/" + other).header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account)).andExpect(status().isOk());
        mvc.perform(put("/api/rooms/" + ROOM + "/owner").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"" + other + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms/" + ROOM + "/invitations").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":\"" + other + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/rooms/" + ROOM + "/start").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account)).andExpect(status().isOk());
        verify(rooms).create(eq(account), argThat(request -> request.visibility() == RoomVisibility.PUBLIC
                && request.maxPlayers() == 4 && request.smallBlind() == 10 && request.bigBlind() == 20));
        verify(rooms).mine(account);
        verify(rooms).get(ROOM, account);
        verify(rooms).join(ROOM, account, "secret12");
        verify(rooms).leave(ROOM, account);
        verify(rooms).ready(ROOM, account, true);
        verify(rooms).kick(ROOM, account, other);
        verify(rooms).transferOwner(ROOM, account, other);
        verify(rooms).invite(ROOM, account, other);
        verify(rooms).start(ROOM, account);
    }

    @Test
    void userCanRegisterAndConfirmButCannotStartOrCancelAdminTournaments() throws Exception {
        for (String operation : List.of("register", "confirm")) {
            mvc.perform(post("/api/tournaments/" + tournament + "/" + operation).header(CompetitionGatewayFilter.HEADER, KEY)
                    .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isOk());
        }
        for (String operation : List.of("start", "cancel")) {
            mvc.perform(post("/api/admin/tournaments/" + tournament + "/" + operation)
                    .header(CompetitionGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                    .header("X-User-Role", "USER")).andExpect(status().isForbidden());
        }
        verify(tournaments).register(tournament, account);
        verify(tournaments).confirm(tournament, account);
        verify(tournaments, never()).start(any());
        verify(tournaments, never()).cancel(any());
    }

    @Test
    void devProfileDoesNotSupplyAdminRoleOrOverrideGatewayAccount() throws Exception {
        mvc.perform(post("/api/admin/tournaments/" + tournament + "/start").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-Dev-User", account)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/tournaments/" + tournament + "/cancel").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms/me").header(CompetitionGatewayFilter.HEADER, KEY).header("X-Dev-User", account))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMP_UNAUTHORIZED"));
        verifyNoInteractions(rooms, tournaments);
        mvc.perform(get("/api/rooms/me").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-Dev-User", other)).andExpect(status().isOk());
        verify(rooms).mine(account);
        verify(rooms, never()).mine(other);
    }

    @Test
    void verifiedAdminCanCreateStartAndCancelUsingTheExistingContract() throws Exception {
        mvc.perform(post("/api/admin/tournaments").header(CompetitionGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Security fixture","type":"OPEN","startAt":"%s","entryFee":100,"slots":8,"rewardCoins":1000}
                        """.formatted(Instant.now().plusSeconds(86400)))).andExpect(status().isOk());
        for (String operation : List.of("start", "cancel")) {
            mvc.perform(post("/api/admin/tournaments/" + tournament + "/" + operation)
                    .header(CompetitionGatewayFilter.HEADER, KEY).header("X-User-Role", "ADMIN")).andExpect(status().isOk());
        }
        verify(tournaments).create(eq(account), argThat(request -> request.type() == TournamentType.OPEN
                && request.entryFee() == 100 && request.slots() == 8 && request.rewardCoins() == 1000));
        verify(tournaments).start(tournament);
        verify(tournaments).cancel(tournament);
    }

    @Test
    void emptyConfigurationDuplicateKeysAndOtherServiceKeysFailClosed() throws Exception {
        client("").perform(get("/api/rooms").header(CompetitionGatewayFilter.HEADER, KEY)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").header(CompetitionGatewayFilter.HEADER, KEY, KEY)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").header("X-Ranking-Internal-Key", KEY).header("X-Ranking-Gateway-Key", KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").header(CompetitionGatewayFilter.HEADER, "ranking-gateway-test-key"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rooms, tournaments);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackageClasses = CompetitionGatewayFilter.class)
    @Import({RoomController.class, TournamentController.class, AdminTournamentController.class,
            AdminAccessValidator.class, CompetitionExceptionHandler.class})
    static class TestConfig {
        @Bean RoomService rooms() { return mock(RoomService.class); }
        @Bean TournamentService tournaments() { return mock(TournamentService.class); }
    }
}
