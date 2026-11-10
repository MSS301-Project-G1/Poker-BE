package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.session.internal-key=session-test-key")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class SessionOwnershipTests {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired JsonMapper json;
    private MockMvc mvc;
    private Cookie csrf;
    private String csrfValue;
    private Account account;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrf = response.getCookie("XSRF-TOKEN");
        csrfValue = json.readTree(response.getContentAsString()).at("/result/token").asString();
        account = accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode("Poker123!" )).accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
    }

    @Test
    void swaggerUsesAuthorizeForTabAndCsrfWithoutDuplicateInputParameters() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = json.readTree(response.getResponse().getContentAsString());
        var login = document.at("/paths/~1api~1auth~1login/post");
        assertThat(login.get("parameters")).isNull();
        assertThat(login.get("security").get(0).has("csrfToken")).isTrue();
        assertThat(login.get("security").get(0).has("tabId")).isTrue();
        assertThat(document.at("/components/securitySchemes/tabId/name").asString()).isEqualTo("X-Tab-Id");
        assertThat(document.at("/paths/~1api~1auth~1csrf/get").get("parameters")).isNull();
    }

    @Test
    void newLoginImmediatelyInvalidatesOldAccessAndRefreshWithoutErasingNewDeviceCookies() throws Exception {
        var first = login(A);
        var second = login(B);
        assertThat(jwt.verify(cookie(first, "accessToken").getValue()).getSubject()).isEqualTo(account.getId().toString());
        mvc.perform(get("/api/auth/me").cookie(cookie(first, "accessToken")).header("X-Tab-Id", A))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_SESSION_REPLACED"));
        mvc.perform(post("/api/auth/refresh").cookie(csrf, cookie(first, "refreshToken"))
                        .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", A))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_SESSION_REPLACED"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/auth/me").cookie(cookie(second, "accessToken")).header("X-Tab-Id", B))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.accountId").value(account.getId().toString()));
    }

    @Test
    void newTabClaimsSameLoginAndOldTabCannotUseSharedNewCookiesToWriteRefreshOrLogout() throws Exception {
        var first = login(A);
        var claim = mvc.perform(post("/api/auth/session/claim").cookie(csrf, cookie(first, "refreshToken"))
                        .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", B))
                .andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(claim.getResponse().getContentAsString()).at("/result/sessionId").asString())
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()).at("/result/sessionId").asString());
        for (String path : new String[]{"/api/auth/me", "/api/auth/session", "/api/auth/ws-token"}) {
            mvc.perform(get(path).cookie(cookie(claim, "accessToken")).header("X-Tab-Id", A))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_TAB_REPLACED"));
        }
        for (String path : new String[]{"/api/auth/refresh", "/api/auth/logout"}) {
            mvc.perform(post(path).cookie(csrf, cookie(claim, "refreshToken"))
                            .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", A))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_TAB_REPLACED"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        mvc.perform(post("/api/checkin").cookie(csrf, cookie(claim, "accessToken"))
                        .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", A))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_TAB_REPLACED"));
        mvc.perform(get("/api/auth/session").cookie(cookie(claim, "accessToken")).header("X-Tab-Id", B))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.tabId").value(B.toString()));
    }

    @Test
    void claimWorksWithExpiredAccessCookieAndKeepsOriginalSevenDayDeadline() throws Exception {
        var first = login(A);
        var claim = mvc.perform(post("/api/auth/session/claim")
                        .cookie(csrf, cookie(first, "refreshToken"), new Cookie("accessToken", "expired"))
                        .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", B))
                .andExpect(status().isOk()).andReturn();
        var before = json.readTree(first.getResponse().getContentAsString()).get("result");
        var after = json.readTree(claim.getResponse().getContentAsString()).get("result");
        assertThat(after.get("refreshExpiresAt").asString()).isEqualTo(before.get("refreshExpiresAt").asString());
        assertThat(after.get("sessionId").asString()).isEqualTo(before.get("sessionId").asString());
        assertThat(cookie(first, "refreshToken").getValue()).isNotEqualTo(cookie(claim, "refreshToken").getValue());
    }

    @Test
    void internalAuthorityRequiresItsOwnKeyAndRejectsStaleTab() throws Exception {
        var first = login(A);
        String bearer = "Bearer " + cookie(first, "accessToken").getValue();
        mvc.perform(get("/internal/auth/session/check").header("Authorization", bearer).header("X-Tab-Id", A))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/auth/session/check").header("Authorization", bearer)
                        .header("X-Tab-Id", A).header("X-Session-Internal-Key", "session-test-key"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/internal/auth/session/check").header("Authorization", bearer)
                        .header("X-Tab-Id", B).header("X-Session-Internal-Key", "session-test-key"))
                .andExpect(status().isConflict()).andExpect(header().string("X-Session-Error", "AUTH_TAB_REPLACED"));
    }

    @Test
    void claimWithoutCsrfCannotChangeOwnerAndActiveTabCanLogout() throws Exception {
        var first = login(A);
        mvc.perform(post("/api/auth/session/claim").cookie(cookie(first, "refreshToken")).header("X-Tab-Id", B))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/session").cookie(cookie(first, "accessToken")).header("X-Tab-Id", A))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").cookie(csrf, cookie(first, "refreshToken"))
                        .header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", A))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(cookie(first, "accessToken")).header("X-Tab-Id", A))
                .andExpect(status().isUnauthorized());
    }

    private MvcResult login(UUID tab) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", account.getEmail(), "password", "Poker123!")))
                        .cookie(csrf).header("X-XSRF-TOKEN", csrfValue).header("X-Tab-Id", tab))
                .andExpect(status().isOk()).andReturn();
    }

    private Cookie cookie(MvcResult response, String name) { return response.getResponse().getCookie(name); }
}
