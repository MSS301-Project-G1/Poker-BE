package com.msspoker.rankingservice.security;

import com.msspoker.rankingservice.config.InternalRankingSecurityConfig;
import com.msspoker.rankingservice.config.RankingGatewaySecurityConfig;
import com.msspoker.rankingservice.controller.AdminSeasonController;
import com.msspoker.rankingservice.controller.InternalRankingController;
import com.msspoker.rankingservice.controller.RankingController;
import com.msspoker.rankingservice.controller.SeasonController;
import com.msspoker.rankingservice.controller.TutorialController;
import com.msspoker.rankingservice.exception.RankingExceptionHandler;
import com.msspoker.rankingservice.service.EloAdjustmentService;
import com.msspoker.rankingservice.service.RankingService;
import com.msspoker.rankingservice.service.SeasonRewardService;
import com.msspoker.rankingservice.service.SeasonService;
import com.msspoker.rankingservice.service.TutorialService;
import com.msspoker.rankingservice.validator.AdminAccessValidator;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(RankingIngressSecurityTest.TestConfig.class)
@WebAppConfiguration
@ActiveProfiles("dev")
class RankingIngressSecurityTest {
    private static final String KEY = "ranking-gateway-test-key";
    private static final String INTERNAL_KEY = "ranking-internal-test-key";
    private final UUID account = UUID.randomUUID();
    private final UUID season = UUID.randomUUID();
    private final UUID match = UUID.randomUUID();

    @Autowired private WebApplicationContext context;
    @Autowired private RankingService ranking;
    @Autowired private SeasonService seasons;
    @Autowired private SeasonRewardService rewards;
    @Autowired private TutorialService tutorials;
    private MockMvc mvc;

    private MockMvc client(String key, String internalKey) {
        var api = new RankingGatewaySecurityConfig().rankingGatewayFilter(key);
        var internal = new InternalRankingSecurityConfig().internalRankingFilter(internalKey);
        assertThat(api.getUrlPatterns()).containsExactly("/api", "/api/*");
        assertThat(internal.getUrlPatterns()).containsExactly("/internal", "/internal/*");
        assertThat(api.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(internal.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        // Include component filters as well, so a dev identity override cannot hide from these checks.
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilter(api.getFilter(), api.getUrlPatterns().toArray(String[]::new))
                .addFilter(internal.getFilter(), internal.getUrlPatterns().toArray(String[]::new))
                .addFilters(context.getBeansOfType(Filter.class).values().toArray(Filter[]::new)).build();
    }

    @BeforeEach
    void setUp() {
        reset(ranking, seasons, rewards, tutorials);
        mvc = client(KEY, INTERNAL_KEY);
    }

    private List<MockHttpServletRequestBuilder> apiRequests() {
        return List.of(get("/api/ranking/leaderboard"), get("/api/ranking/me"), get("/api/ranking/friends"),
                get("/api/ranking/me/matches/" + match), get("/api/ranking/players/" + account), get("/api/seasons/current"),
                get("/api/seasons/" + season + "/rewards"), get("/api/seasons/rewards/me"),
                post("/api/seasons/" + season + "/rewards/claim"), get("/api/tutorial/me"), post("/api/tutorial/complete"),
                get("/api/admin/seasons"), post("/api/admin/seasons"),
                put("/api/admin/seasons/" + season + "/rewards"), post("/api/admin/seasons/close"),
                put("/api/admin/seasons/players/" + account + "/elo"),
                get("/api/admin/seasons/players/" + account + "/elo-adjustments"));
    }

    @Test
    void forgedIdentityCannotReachAnyPublicUserOrAdminEndpointWithoutValidGatewayKey() throws Exception {
        for (String key : List.of("", "wrong")) {
            for (var request : apiRequests()) {
                request.header("X-User-Id", account).header("X-User-Role", "ADMIN").header("X-Dev-User", account);
                if (!key.isEmpty()) request.header(RankingGatewayFilter.HEADER, key);
                mvc.perform(request).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("RANK_FORBIDDEN"));
            }
        }
        verifyNoInteractions(ranking, seasons, rewards, tutorials);
    }

    @Test
    void gatewayAllowsAnonymousLeaderboardAndSeasonReadsWithOriginalPagination() throws Exception {
        mvc.perform(get("/api/ranking/leaderboard?page=2&size=7").header(RankingGatewayFilter.HEADER, KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/seasons/current").header(RankingGatewayFilter.HEADER, KEY)).andExpect(status().isOk());
        mvc.perform(get("/api/seasons/" + season + "/rewards").header(RankingGatewayFilter.HEADER, KEY))
                .andExpect(status().isOk());
        verify(ranking).leaderboard(2, 7);
        verify(seasons).current();
        verify(seasons).rewards(season);
    }

    @Test
    void trustedUserKeepsIdentityForRankingTutorialAndRewardsButCannotActAsAdmin() throws Exception {
        for (String path : List.of("/api/ranking/me", "/api/ranking/friends", "/api/ranking/me/matches/" + match,
                "/api/seasons/rewards/me", "/api/tutorial/me")) {
            mvc.perform(get(path).header(RankingGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                    .header("X-User-Role", "USER")).andExpect(status().isOk());
        }
        mvc.perform(post("/api/seasons/" + season + "/rewards/claim").header(RankingGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isOk());
        mvc.perform(post("/api/tutorial/complete").header(RankingGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/seasons").header(RankingGatewayFilter.HEADER, KEY)
                .header("X-User-Id", account).header("X-User-Role", "USER")).andExpect(status().isForbidden());
        verify(ranking).me(account);
        verify(ranking).friends(account);
        verify(ranking).matchRating(account, match);
        verify(rewards).mine(account);
        verify(rewards).claim(season, account);
        verify(tutorials).status(account);
        verify(tutorials).complete(account);
        verifyNoInteractions(seasons);
    }

    @Test
    void devProfileNeitherDefaultsToAdminNorOverridesGatewayIdentity() throws Exception {
        mvc.perform(get("/api/admin/seasons").header(RankingGatewayFilter.HEADER, KEY).header("X-Dev-User", account))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/seasons").header(RankingGatewayFilter.HEADER, KEY).header("X-User-Id", account))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ranking/me").header(RankingGatewayFilter.HEADER, KEY).header("X-Dev-User", account))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ranking, seasons);
        mvc.perform(get("/api/ranking/me").header(RankingGatewayFilter.HEADER, KEY).header("X-User-Id", account)
                .header("X-Dev-User", UUID.randomUUID())).andExpect(status().isOk());
        verify(ranking).me(account);
        mvc.perform(get("/api/admin/seasons").header(RankingGatewayFilter.HEADER, KEY).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/seasons/close").header(RankingGatewayFilter.HEADER, KEY).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        verify(seasons).list();
        verify(seasons).close();
    }

    @Test
    void internalReadsRejectMissingOrWrongKeyEvenWithForgedAdmin() throws Exception {
        for (String key : List.of("", "wrong")) {
            for (String path : List.of("/internal/ranking/" + account, "/internal/ranking/top?limit=7")) {
                var request = get(path).header("X-User-Id", account).header("X-User-Role", "ADMIN");
                if (!key.isEmpty()) request.header(InternalRankingFilter.HEADER, key);
                mvc.perform(request).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("RANK_INTERNAL_FORBIDDEN"));
            }
        }
        verifyNoInteractions(ranking);
    }

    @Test
    void internalReadsWorkWithTheirOwnKeyAndCannotUseGatewayCredentials() throws Exception {
        mvc.perform(get("/internal/ranking/" + account).header(RankingGatewayFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/ranking/" + account).header(InternalRankingFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ranking/me").header(InternalRankingFilter.HEADER, INTERNAL_KEY).header("X-User-Id", account))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ranking/me").header(RankingGatewayFilter.HEADER, INTERNAL_KEY).header("X-User-Id", account))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ranking);
        mvc.perform(get("/internal/ranking/" + account).header(InternalRankingFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk());
        mvc.perform(get("/internal/ranking/top?limit=7").header(InternalRankingFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk());
        verify(ranking).ranking(account);
        verify(ranking).top(7);
        verifyNoMoreInteractions(ranking);
    }

    @Test
    void blankConfigurationAndDuplicateHeadersFailClosedOnBothIngresses() throws Exception {
        client("", INTERNAL_KEY).perform(get("/api/ranking/leaderboard").header(RankingGatewayFilter.HEADER, KEY))
                .andExpect(status().isForbidden());
        client(KEY, "").perform(get("/internal/ranking/top").header(InternalRankingFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/ranking/leaderboard").header(RankingGatewayFilter.HEADER, KEY, KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/ranking/top").header(InternalRankingFilter.HEADER, INTERNAL_KEY, INTERNAL_KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ranking);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackageClasses = RankingGatewayFilter.class)
    @Import({RankingController.class, SeasonController.class, AdminSeasonController.class, TutorialController.class,
            InternalRankingController.class, AdminAccessValidator.class, RankingExceptionHandler.class})
    static class TestConfig {
        @Bean RankingService ranking() { return mock(RankingService.class); }
        @Bean SeasonService seasons() { return mock(SeasonService.class); }
        @Bean SeasonRewardService rewards() { return mock(SeasonRewardService.class); }
        @Bean TutorialService tutorials() { return mock(TutorialService.class); }
        @Bean EloAdjustmentService eloAdjustments() { return mock(EloAdjustmentService.class); }
    }
}
