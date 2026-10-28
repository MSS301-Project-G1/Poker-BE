package com.msspoker.authservice;

import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class ProfileBatchTests {
    private static final String PATH = "/api/profiles/batch";
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtService jwtService;
    private MockMvc mvc;
    private Cookie access;
    private Account caller;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        caller = account();
        access = new Cookie("accessToken", jwtService.issue(caller, Instant.now().plusSeconds(900)));
    }

    @Test
    void returnsOnlyPublicFieldsInRequestOrderAndSkipsDuplicatesMissingOrDeletedProfiles() throws Exception {
        Account first = account();
        Account second = account();
        Account deleted = account();
        Account withoutProfile = account();
        profile(first, "Khanh");
        profile(second, "Nam");
        profile(deleted, "Deleted user");
        deleted.setDeleted(true);
        accounts.saveAndFlush(deleted);
        entityManager.clear();

        String ids = second.getId() + "," + UUID.randomUUID() + "," + first.getId() + ","
                + second.getId() + "," + deleted.getId() + "," + withoutProfile.getId();
        mvc.perform(get(PATH).param("ids", ids).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("success_request"))
                .andExpect(jsonPath("$.path").value(PATH))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.result", hasSize(2)))
                .andExpect(jsonPath("$.result[0]", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.result[0].accountId").value(second.getId().toString()))
                .andExpect(jsonPath("$.result[0].displayName").value("Nam"))
                .andExpect(jsonPath("$.result[0].avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.result[1].accountId").value(first.getId().toString()))
                .andExpect(jsonPath("$.result[1].displayName").value("Khanh"))
                .andExpect(jsonPath("$.result[1]", org.hamcrest.Matchers.aMapWithSize(3)));
    }

    @Test
    void acceptsRepeatedIdsQueryParametersWithoutCsrfAndKeepsNullAvatar() throws Exception {
        Account target = account();
        profiles.saveAndFlush(Profile.builder().account(target).displayName("Khanh").build());

        mvc.perform(get(PATH).param("ids", target.getId().toString(), target.getId().toString()).cookie(access))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].accountId").value(target.getId().toString()))
                .andExpect(jsonPath("$.result[0].avatarUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void returnsAnEmptyListWhenNoProfilesExist() throws Exception {
        mvc.perform(get(PATH).param("ids", UUID.randomUUID().toString()).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(0)));
    }

    @Test
    void searchesDuplicateNamesUsingPublicCodesAndHidesPrivateFieldsAndUnavailableAccounts() throws Exception {
        Account first = account();
        Account second = account();
        Account deleted = account();
        Account inactive = account();
        profile(first, "FindMe");
        profile(second, "FindMe");
        profile(caller, "FindMe");
        profile(deleted, "FindMe");
        profile(inactive, "FindMe");
        deleted.setDeleted(true);
        accounts.saveAndFlush(deleted);
        inactive.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        accounts.saveAndFlush(inactive);
        entityManager.clear();
        String code = profiles.findById(first.getId()).orElseThrow().getPlayerCode();
        org.junit.jupiter.api.Assertions.assertTrue(code.matches("PK[0-9]+"));
        org.junit.jupiter.api.Assertions.assertNotEquals(code,
                profiles.findById(second.getId()).orElseThrow().getPlayerCode());

        mvc.perform(get("/api/profiles/search").param("q", "findme").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(2)))
                .andExpect(jsonPath("$.result[0]", org.hamcrest.Matchers.aMapWithSize(4)))
                .andExpect(jsonPath("$.result[0].email").doesNotExist());
        mvc.perform(get("/api/profiles/search").param("q", code.toLowerCase()).cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].accountId").value(first.getId().toString()))
                .andExpect(jsonPath("$.result[0].playerCode").value(code));
        mvc.perform(get("/api/profiles/search").param("q", "%_").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result", hasSize(0)));
    }

    @Test
    void ownCodeStaysTheSameAfterChangingNameAndSearchRequiresAuthenticationAndValidQuery() throws Exception {
        profile(caller, "Before");
        Profile own = profiles.findById(caller.getId()).orElseThrow();
        String code = own.getPlayerCode();
        own.setDisplayName("After");
        profiles.saveAndFlush(own);
        entityManager.clear();
        mvc.perform(get("/api/profiles/players/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.playerCode").value(code))
                .andExpect(jsonPath("$.result.displayName").value("After"));
        mvc.perform(get("/api/profiles/search").param("q", "After"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profiles/players/me"))
                .andExpect(status().isUnauthorized());
        for (String query : new String[]{"", "a", "x".repeat(101)}) {
            mvc.perform(get("/api/profiles/search").param("q", query).cookie(access))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void acceptsOneHundredIdsButRejectsOneHundredAndOneBeforeDeduplication() throws Exception {
        String id = UUID.randomUUID().toString();
        String oneHundred = IntStream.range(0, 100).mapToObj(index -> id).collect(Collectors.joining(","));
        mvc.perform(get(PATH).param("ids", oneHundred).cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result", hasSize(0)));
        mvc.perform(get(PATH).param("ids", oneHundred + "," + id).cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"));
    }

    @Test
    void rejectsMissingEmptyMalformedAndMixedInvalidIdsWithStandardErrors() throws Exception {
        mvc.perform(get(PATH).cookie(access))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"));
        for (String ids : new String[]{"", " ", "not-a-uuid", UUID.randomUUID() + ",bad-id", UUID.randomUUID() + ",," + UUID.randomUUID()}) {
            mvc.perform(get(PATH).param("ids", ids).cookie(access).header("X-Request-Id", "profile-review"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"))
                    .andExpect(jsonPath("$.requestId").value("profile-review"));
        }
    }

    @Test
    void requiresAValidAccessCookieAndDoesNotTrustIdentityOrBearerHeaders() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get(PATH).param("ids", id).header("X-User-Id", id).header("X-User-Role", "ADMIN")
                        .header("Authorization", "Bearer " + access.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
        mvc.perform(get(PATH).param("ids", id).cookie(new Cookie("accessToken", "invalid-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHENTICATED"));
    }

    private Account account() {
        return accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("private-password-hash").phoneNumber("0901234567")
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
    }

    private void profile(Account account, String displayName) {
        profiles.saveAndFlush(Profile.builder().account(account).displayName(displayName)
                .avatarUrl("https://example.com/avatar.png").fullName("Private full name")
                .birthDate(LocalDate.of(2000, 1, 1)).gender(GenderEnum.MALE)
                .bio("Private biography").countryCode("VN").city("Private city").build());
    }
}
