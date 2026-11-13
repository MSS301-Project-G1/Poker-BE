package com.msspoker.authservice;

import com.msspoker.authservice.dto.auth.request.LoginRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountModerationEventRepository;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.repository.RefreshTokenRepository;
import com.msspoker.authservice.service.AuthService;
import com.msspoker.authservice.shared.infrastructure.security.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AdminModerationTests {
    private static final String PASSWORD = "Poker123!";
    private static final UUID TAB = UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired AccountRepository accounts;
    @Autowired ProfileRepository profiles;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AccountModerationEventRepository moderationEvents;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender mail;
    private MockMvc mvc;
    private Account admin;
    private Account user;
    private Cookie adminAccess;
    private Cookie csrf;
    private String csrfValue;

    @BeforeEach
    void setup() throws Exception {
        reset(mail);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").header("X-Tab-Id", TAB)).build();
        admin = account(AccountRole.ADMIN, true);
        user = account(AccountRole.USER, true);
        profile(user, "Offensive name", "Spam bio");
        adminAccess = access(login(admin));
        var response = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrf = response.getCookie("XSRF-TOKEN");
        csrfValue = json.readTree(response.getContentAsString()).at("/result/token").asString();
    }

    @Test
    void detailsShowTheWholeAccountWithoutCredentialsAndOnlyToAdmins() throws Exception {
        String body = mvc.perform(get("/api/admin/users/" + user.getId() + "/details").cookie(adminAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(user.getId().toString()))
                .andExpect(jsonPath("$.result.email").value(user.getEmail()))
                .andExpect(jsonPath("$.result.displayName").value("Offensive name"))
                .andExpect(jsonPath("$.result.fullName").value("Full Name"))
                .andExpect(jsonPath("$.result.bio").value("Spam bio"))
                .andExpect(jsonPath("$.result.playerCode").isNotEmpty())
                .andExpect(jsonPath("$.result.customAvatar").value(false))
                .andExpect(jsonPath("$.result.role").value("USER"))
                .andExpect(jsonPath("$.result.emailVerified").value(true))
                .andExpect(jsonPath("$.result.banned").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash", "refreshToken", "tokenHash");

        Cookie userAccess = access(login(user));
        mvc.perform(get("/api/admin/users/" + admin.getId() + "/details").cookie(userAccess))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/" + UUID.randomUUID() + "/details").cookie(adminAccess))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileActionsChangeOnlyTheirFieldAndKeepWhatTheyRemoved() throws Exception {
        String code = profiles.findById(user.getId()).orElseThrow().getPlayerCode();
        mvc.perform(moderate(user, "RESET_DISPLAY_NAME", "  Offensive name  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.displayName").value("Player " + code));
        mvc.perform(moderate(user, "CLEAR_BIO", "Spam link")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bio").doesNotExist());
        // Nothing left to remove: refused, and no history row is written.
        mvc.perform(moderate(user, "CLEAR_BIO", "Again")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_CONFLICT"));
        mvc.perform(moderate(user, "REMOVE_AVATAR", "Default already")).andExpect(status().isConflict());
        mvc.perform(moderate(user, "RESET_DISPLAY_NAME", " ")).andExpect(status().isBadRequest());

        Profile saved = profiles.findById(user.getId()).orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("Player " + code);
        assertThat(saved.getBio()).isNull();
        assertThat(saved.getFullName()).isEqualTo("Full Name");
        mvc.perform(get("/api/admin/users/" + user.getId() + "/moderation").cookie(adminAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.content[0].action").value("CLEAR_BIO"))
                .andExpect(jsonPath("$.result.content[0].previousValue").value("Spam bio"))
                .andExpect(jsonPath("$.result.content[1].action").value("RESET_DISPLAY_NAME"))
                .andExpect(jsonPath("$.result.content[1].reason").value("Offensive name"))
                .andExpect(jsonPath("$.result.content[1].previousValue").value("Offensive name"))
                .andExpect(jsonPath("$.result.content[1].actorId").value(admin.getId().toString()));
    }

    @Test
    void revokingSessionsEndsEveryDeviceAndAdminsAreProtected() throws Exception {
        login(user);
        mvc.perform(moderate(user, "REVOKE_SESSIONS", "Shared account")).andExpect(status().isOk());
        assertThat(tokens.findAll().stream().filter(token -> token.getAccountId().equals(user.getId())))
                .isNotEmpty().allMatch(token -> token.getRevokedAt() != null);

        Account otherAdmin = account(AccountRole.ADMIN, true);
        mvc.perform(moderate(otherAdmin, "REVOKE_SESSIONS", "Not allowed")).andExpect(status().isForbidden());
        mvc.perform(moderate(admin, "REVOKE_SESSIONS", "Not allowed")).andExpect(status().isForbidden());
        assertThat(moderationEvents.findAll()).noneMatch(event -> event.getAccountId().equals(otherAdmin.getId())
                || event.getAccountId().equals(admin.getId()));
    }

    @Test
    void recoveryEmailsReportThrottlingAndUnverifiedStatesToTheAdmin() throws Exception {
        mvc.perform(moderate(user, "SEND_PASSWORD_RESET", "Locked out")).andExpect(status().isOk());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        mvc.perform(moderate(user, "SEND_PASSWORD_RESET", "Again")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_OTP_RESEND_TOO_SOON"));
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        mvc.perform(moderate(user, "RESEND_VERIFICATION", "Already verified")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_VERIFIED"));

        Account pending = account(AccountRole.USER, false);
        mvc.perform(moderate(pending, "SEND_PASSWORD_RESET", "Not verified")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_VERIFICATION_PENDING"));
        assertThat(moderationEvents.findAll().stream().filter(event -> event.getAccountId().equals(user.getId())))
                .hasSize(1);
    }

    private MockHttpServletRequestBuilder moderate(Account target, String action, String reason) {
        return post("/api/admin/users/" + target.getId() + "/moderation").cookie(adminAccess, csrf)
                .header("X-XSRF-TOKEN", csrfValue).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("action", action, "reason", reason)));
    }

    private Account account(AccountRole role, boolean verified) {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode(PASSWORD))
                .accountStatus(verified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION)
                .emailVerified(verified).role(role).build());
    }

    private void profile(Account account, String displayName, String bio) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var managed = accounts.findById(account.getId()).orElseThrow();
            profiles.saveAndFlush(Profile.builder().account(managed).displayName(displayName).fullName("Full Name")
                    .bio(bio).build());
        });
    }

    private SessionTokens login(Account account) { return auth.login(new LoginRequest(account.getEmail(), PASSWORD), TAB); }
    private Cookie access(SessionTokens session) { return new Cookie("accessToken", session.accessToken()); }
}
