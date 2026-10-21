package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountRole;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.enums.GenderEnum;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class MyProfileTests {
    private static final String PATH = "/api/profiles/me";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private JsonMapper json;
    private MockMvc mvc;
    private Account caller;
    private Cookie access;
    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        caller = account();
        profile(caller);
        access = access(caller);
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        assertThat(csrf.getStatus()).isEqualTo(200);
        csrfCookie = csrf.getCookie("XSRF-TOKEN");
        csrfToken = json.readTree(csrf.getContentAsString()).get("result").get("token").asString();
    }

    @Test
    void readsPrivateFieldsOnlyForTheUserInTheAccessCookie() throws Exception {
        Account other = account();
        profile(other);
        mvc.perform(get(PATH).cookie(access).param("accountId", other.getId().toString())
                        .header("X-User-Id", other.getId()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("success_request"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.result.accountId").value(caller.getId().toString()))
                .andExpect(jsonPath("$.result.email").value(caller.getEmail()))
                .andExpect(jsonPath("$.result.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.result.fullName").value("Private name"))
                .andExpect(jsonPath("$.result.birthDate").value("2000-01-01"))
                .andExpect(jsonPath("$.result.gender").value("MALE"))
                .andExpect(jsonPath("$.result.bio").value("Private bio"))
                .andExpect(jsonPath("$.result.countryCode").value("VN"))
                .andExpect(jsonPath("$.result.city").value("Old city"))
                .andExpect(jsonPath("$.result.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.result.firstLoginRewarded").doesNotExist());
    }

    @Test
    void updatesOwnProfileAndPhoneAtomicallyAndKeepsPublicResponsesMinimal() throws Exception {
        Account other = account();
        profile(other);
        String body = """
                {"displayName":"  Khanh mới  ","fullName":"  Nguyễn Khánh  ",
                 "phoneNumber":"  +84901234567  ","gender":"OTHER","birthDate":"2003-05-12",
                 "bio":"  Hello Poker  ","countryCode":"vn","city":"  Hồ Chí Minh  "}
                """;
        mvc.perform(update(body).cookie(access).param("accountId", other.getId().toString())
                        .header("X-User-Id", other.getId()).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(caller.getId().toString()))
                .andExpect(jsonPath("$.result.displayName").value("Khanh mới"))
                .andExpect(jsonPath("$.result.fullName").value("Nguyễn Khánh"))
                .andExpect(jsonPath("$.result.phoneNumber").value("+84901234567"))
                .andExpect(jsonPath("$.result.countryCode").value("VN"))
                .andExpect(jsonPath("$.result.avatarUrl").value("https://example.com/original.png"));
        entityManager.flush();
        entityManager.clear();

        Account stored = accounts.findById(caller.getId()).orElseThrow();
        Profile storedProfile = profiles.findById(caller.getId()).orElseThrow();
        assertThat(stored.getPhoneNumber()).isEqualTo("+84901234567");
        assertThat(stored.getEmail()).isEqualTo(caller.getEmail());
        assertThat(stored.getRole()).isEqualTo(AccountRole.USER);
        assertThat(stored.getPasswordHash()).isEqualTo("private-password-hash");
        assertThat(stored.isFirstLoginRewarded()).isFalse();
        assertThat(storedProfile.getFullName()).isEqualTo("Nguyễn Khánh");
        assertThat(storedProfile.getGender()).isEqualTo(GenderEnum.OTHER);
        assertThat(storedProfile.getBirthDate()).isEqualTo(LocalDate.of(2003, 5, 12));
        assertThat(storedProfile.getBio()).isEqualTo("Hello Poker");
        assertThat(storedProfile.getCity()).isEqualTo("Hồ Chí Minh");
        assertThat(profiles.findById(other.getId()).orElseThrow().getDisplayName()).isEqualTo("Original");
        assertThat(accounts.findById(other.getId()).orElseThrow().getPhoneNumber()).isEqualTo("0901234567");

        mvc.perform(get("/api/profiles/batch").param("ids", caller.getId().toString()).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0]", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.result[0].displayName").value("Khanh mới"));
        mvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profile", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.result.profile.displayName").value("Khanh mới"));
    }

    @Test
    void putClearsOmittedNullOrBlankOptionalFieldsButKeepsAvatar() throws Exception {
        mvc.perform(update("""
                {"displayName":"Original","fullName":"  ","phoneNumber":null,"bio":""}
                """).cookie(access)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Profile stored = profiles.findById(caller.getId()).orElseThrow();
        assertThat(stored.getDisplayName()).isEqualTo("Original");
        assertThat(stored.getFullName()).isNull();
        assertThat(stored.getGender()).isNull();
        assertThat(stored.getBirthDate()).isNull();
        assertThat(stored.getBio()).isNull();
        assertThat(stored.getCountryCode()).isNull();
        assertThat(stored.getCity()).isNull();
        assertThat(stored.getAvatarUrl()).isEqualTo("https://example.com/original.png");
        assertThat(accounts.findById(caller.getId()).orElseThrow().getPhoneNumber()).isNull();
    }

    @Test
    void rejectsInvalidFieldsBeforeChangingAccountOrProfile() throws Exception {
        String tomorrow = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString();
        for (String body : List.of("{}", "null", "{", "{\"displayName\":\"  \"}",
                "{\"displayName\":\"" + "x".repeat(101) + "\"}",
                "{\"displayName\":\"New\",\"fullName\":\"" + "x".repeat(151) + "\"}",
                "{\"displayName\":\"New\",\"bio\":\"" + "x".repeat(501) + "\"}",
                "{\"displayName\":\"New\",\"city\":\"" + "x".repeat(101) + "\"}",
                "{\"displayName\":\"New\",\"phoneNumber\":\"abc1234\"}",
                "{\"displayName\":\"New\",\"phoneNumber\":\"123456\"}",
                "{\"displayName\":\"New\",\"phoneNumber\":\"1234567890123456\"}",
                "{\"displayName\":\"New\",\"countryCode\":\"V1\"}",
                "{\"displayName\":\"New\",\"countryCode\":\"VNM\"}",
                "{\"displayName\":\"New\",\"gender\":\"INVALID\"}",
                "{\"displayName\":\"New\",\"birthDate\":\"2026-02-30\"}",
                "{\"displayName\":\"New\",\"birthDate\":\"" + tomorrow + "\"}")) {
            mvc.perform(update(body).cookie(access).header("X-Request-Id", "my-profile-review"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"))
                    .andExpect(jsonPath("$.requestId").value("my-profile-review"));
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(profiles.findById(caller.getId()).orElseThrow().getDisplayName()).isEqualTo("Original");
        assertThat(accounts.findById(caller.getId()).orElseThrow().getPhoneNumber()).isEqualTo("0901234567");
    }

    @Test
    void requiresAccessCookiesAndDoesNotAcceptClientSuppliedIdentity() throws Exception {
        mvc.perform(get(PATH).header("X-User-Id", caller.getId()).header("Authorization", "Bearer " + access.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(update("{\"displayName\":\"New\"}").header("X-User-Id", caller.getId()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).cookie(new Cookie("accessToken", "invalid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMissingOrMismatchedCsrfWithoutUpdatingTheProfile() throws Exception {
        mvc.perform(put(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(put(PATH).cookie(access, csrfCookie).header("X-XSRF-TOKEN", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        assertThat(profiles.findById(caller.getId()).orElseThrow().getDisplayName()).isEqualTo("Original");
    }

    @Test
    void rejectsDeletedMissingAndUnverifiedAccountsEvenWithASignedJwt() throws Exception {
        Account deleted = account();
        Cookie deletedAccess = access(deleted);
        deleted.setDeleted(true);
        accounts.saveAndFlush(deleted);
        Account missing = Account.builder().id(UUID.randomUUID()).email("missing@example.com").build();
        Account pending = account();
        pending.setEmailVerified(false);
        pending.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        accounts.saveAndFlush(pending);

        for (Cookie cookie : List.of(deletedAccess, access(missing))) {
            mvc.perform(get(PATH).cookie(cookie)).andExpect(status().isUnauthorized());
            mvc.perform(update("{\"displayName\":\"New\"}").cookie(cookie)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(PATH).cookie(access(pending))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_VERIFICATION_PENDING"));
        mvc.perform(update("{\"displayName\":\"New\"}").cookie(access(pending))).andExpect(status().isConflict());
    }

    @Test
    void reportsAMissingProfileInsteadOfCreatingOneDuringReadOrUpdate() throws Exception {
        Account withoutProfile = account();
        Cookie cookie = access(withoutProfile);
        mvc.perform(get(PATH).cookie(cookie)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_FOUND"));
        mvc.perform(update("{\"displayName\":\"New\"}").cookie(cookie)).andExpect(status().isNotFound());
        assertThat(profiles.findById(withoutProfile.getId())).isEmpty();
    }

    @Test
    void allowsCredentialedPutPreflightFromTheConfiguredFrontendOrigin() throws Exception {
        mvc.perform(options(PATH).header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PUT")));
    }

    @Test
    void missingS3ConfigurationKeepsProfilesAvailableAndCatalogEmpty() throws Exception {
        mvc.perform(get(PATH).cookie(access)).andExpect(status().isOk());
        mvc.perform(get(PATH + "/avatar/presign").cookie(access).param("fileName", "avatar.png")
                        .param("contentType", "image/png").param("fileSize", "100"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_AVATAR_STORAGE_UNAVAILABLE"));
        mvc.perform(get("/api/profiles/avatars").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
        mvc.perform(put(PATH + "/avatar").cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder update(String body) {
        return put(PATH).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private Cookie access(Account account) {
        return new Cookie("accessToken", jwtService.issue(account, Instant.now().plusSeconds(900)));
    }

    private Account account() {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("private-password-hash").phoneNumber("0901234567")
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
    }

    private void profile(Account account) {
        profiles.saveAndFlush(Profile.builder().account(account).displayName("Original")
                .fullName("Private name").avatarUrl("https://example.com/original.png").gender(GenderEnum.MALE)
                .birthDate(LocalDate.of(2000, 1, 1)).bio("Private bio").countryCode("VN").city("Old city").build());
    }
}
