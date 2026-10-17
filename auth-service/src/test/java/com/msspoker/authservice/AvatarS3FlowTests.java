package com.msspoker.authservice;

import com.msspoker.authservice.dto.profile.request.ConfirmAvatarRequest;
import com.msspoker.authservice.entity.Account;
import com.msspoker.authservice.entity.Profile;
import com.msspoker.authservice.enums.AccountStatus;
import com.msspoker.authservice.repository.AccountRepository;
import com.msspoker.authservice.repository.ProfileRepository;
import com.msspoker.authservice.service.ProfileService;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AvatarS3FlowTests {
    private static final String PATH = "/api/profiles/me/avatar";
    private static final S3TestServer S3 = new S3TestServer();
    @Autowired private WebApplicationContext context;
    @Autowired private AccountRepository accounts;
    @Autowired private ProfileRepository profiles;
    @Autowired private JwtService jwtService;
    @Autowired private JsonMapper json;
    @Autowired private ProfileService avatars;
    @Autowired private PlatformTransactionManager transactions;
    private MockMvc mvc;
    private Account caller;
    private Cookie access;
    private Cookie csrfCookie;
    private String csrfToken;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("auth.s3.enabled", () -> "true");
        registry.add("auth.s3.bucket", () -> "test-bucket");
        registry.add("auth.s3.region", () -> "ap-southeast-1");
        registry.add("auth.s3.access-key", () -> "test-access-key");
        registry.add("auth.s3.secret-key", () -> "test-secret-key-not-real");
        registry.add("auth.s3.endpoint", S3::url);
        registry.add("auth.s3.max-file-size", () -> "2MB");
        registry.add("auth.avatar.public-base-url", () -> "http://localhost:8080");
    }

    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        caller = new TransactionTemplate(transactions).execute(status -> {
            Account account = accounts.saveAndFlush(Account.builder().email(UUID.randomUUID() + "@example.com")
                    .passwordHash("private-hash").accountStatus(AccountStatus.ACTIVE).emailVerified(true).build());
            profiles.saveAndFlush(Profile.builder().account(account).displayName("Khanh").build());
            return account;
        });
        access = new Cookie("accessToken", jwtService.issue(caller, Instant.now().plusSeconds(900)));
        var csrf = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        csrfCookie = csrf.getCookie("XSRF-TOKEN");
        csrfToken = json.readTree(csrf.getContentAsString()).get("result").get("token").asString();
    }

    @AfterAll
    static void closeS3() { S3.close(); }

    @Test
    void listsAndSelectsBundledPresetsWithoutChangingOtherProfileFields() throws Exception {
        mvc.perform(get("/api/profiles/avatars")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profiles/avatars").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(3));
        mvc.perform(get("/api/profiles/avatars/presets/spade").cookie(access)).andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"));
        mvc.perform(put(PATH).cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"heart\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.displayName").value("Khanh"))
                .andExpect(jsonPath("$.result.avatarUrl").value("http://localhost:8080/api/profiles/avatars/presets/heart"));
    }

    @Test
    void presignsDirectPutThenValidatesAndStoresAStableKeyWithFreshPrivateGetUrls() throws Exception {
        byte[] image = image("png", 32, 24);
        JsonNode presign = presign("image/png", image.length);
        assertThat(presign.get("key").asString()).startsWith("avatars/staging/" + caller.getId() + "/");
        assertThat(presign.get("uploadUrl").asString()).contains("X-Amz-Signature=", "X-Amz-Expires=300")
                .doesNotContain("test-secret-key-not-real");
        assertThat(presign.get("method").asString()).isEqualTo("PUT");
        assertThat(Instant.parse(presign.get("expiresAt").asString())).isAfter(Instant.now());
        assertThat(profiles.findById(caller.getId()).orElseThrow().getAvatarUrl()).isNull();
        putS3(presign, image);
        mvc.perform(confirm(presign.get("key").asString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accountId").value(caller.getId().toString()));
        String stored = profiles.findById(caller.getId()).orElseThrow().getAvatarUrl();
        assertThat(stored).startsWith("avatars/users/" + caller.getId() + "/").doesNotContain("X-Amz");
        assertThat(S3.bytes(stored)).isEqualTo(image);
        assertThat(S3.exists(presign.get("key").asString())).isFalse();
        var profile = result(mvc.perform(get("/api/profiles/me").cookie(access)).andExpect(status().isOk()).andReturn());
        assertThat(profile.get("avatarUrl").asString()).contains("X-Amz-Signature=", "X-Amz-Expires=3600");
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(profile.get("avatarUrl").asString())).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.body()).isEqualTo(image);
        }
        mvc.perform(get("/api/profiles/batch").cookie(access).param("ids", caller.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result[0]", org.hamcrest.Matchers.aMapWithSize(3)))
                .andExpect(jsonPath("$.result[0].avatarUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature=")));
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profile.avatarUrl").value(org.hamcrest.Matchers.containsString("X-Amz-Signature=")));
        // Repeating confirmation succeeds even after staging cleanup.
        mvc.perform(confirm(presign.get("key").asString())).andExpect(status().isOk());
        // Reusing the still-valid upload URL changes only staging, never the published avatar.
        putS3(presign, "not-an-image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(S3.bytes(stored)).isEqualTo(image);
    }

    @Test
    void jpegUploadsWorkAndChoosingAPresetRemovesThePreviousOwnedS3Object() throws Exception {
        byte[] image = image("jpg", 24, 16);
        JsonNode presign = presign("image/jpeg", image.length);
        putS3(presign, image);
        mvc.perform(confirm(presign.get("key").asString())).andExpect(status().isOk());
        String stored = profiles.findById(caller.getId()).orElseThrow().getAvatarUrl();
        assertThat(stored).endsWith(".jpg");
        mvc.perform(put(PATH).cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarId\":\"spade\"}"))
                .andExpect(status().isOk());
        assertThat(S3.exists(stored)).isFalse();
    }

    @Test
    void refusesMissingUploadsForeignKeysOrClientProvidedUrls() throws Exception {
        JsonNode presign = presign("image/png", 100);
        mvc.perform(confirm(presign.get("key").asString())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_AVATAR_UPLOAD_NOT_FOUND"));
        for (String key : List.of("avatars/staging/" + UUID.randomUUID() + "/" + UUID.randomUUID() + ".png",
                "https://example.com/avatar.png", "avatars/staging/" + caller.getId() + "/../other.png")) {
            mvc.perform(confirm(key)).andExpect(status().isForbidden());
        }
        assertThat(profiles.findById(caller.getId()).orElseThrow().getAvatarUrl()).isNull();
    }

    @Test
    void rejectsInvalidPresignInputsAndChecksTheActualObjectSizeAndImageDataAtConfirmation() throws Exception {
        mvc.perform(presignRequest("image/svg+xml", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(presignRequest("image/png", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(presignRequest("image/png", "2097153"))
                .andExpect(status().isPayloadTooLarge());
        for (byte[] bytes : List.of("<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), image("png", 2049, 1))) {
            JsonNode presign = presign("image/png", bytes.length);
            putS3(presign, bytes);
            mvc.perform(confirm(presign.get("key").asString())).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_AVATAR_INVALID"));
        }
        JsonNode smallDeclaration = presign("image/png", 100);
        putS3(smallDeclaration, new byte[2097153]);
        mvc.perform(confirm(smallDeclaration.get("key").asString())).andExpect(status().isPayloadTooLarge());
        assertThat(profiles.findById(caller.getId()).orElseThrow().getAvatarUrl()).isNull();
    }

    @Test
    void rollsBackThePublishedCopyAndKeepsThePreviousAvatarAndStagingForRetry() throws Exception {
        byte[] bytes = image("png", 16, 16);
        JsonNode first = presign("image/png", bytes.length);
        putS3(first, bytes);
        mvc.perform(confirm(first.get("key").asString())).andExpect(status().isOk());
        String previous = profiles.findById(caller.getId()).orElseThrow().getAvatarUrl();
        JsonNode replacement = presign("image/png", bytes.length);
        putS3(replacement, bytes);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            avatars.confirmUpload(caller.getId(), new ConfirmAvatarRequest(replacement.get("key").asString()));
            status.setRollbackOnly();
        });
        assertThat(profiles.findById(caller.getId()).orElseThrow().getAvatarUrl()).isEqualTo(previous);
        assertThat(S3.exists(previous)).isTrue();
        assertThat(S3.exists(replacement.get("key").asString())).isTrue();
        assertThat(S3.exists(replacement.get("key").asString().replace("/staging/", "/users/"))).isFalse();
    }

    @Test
    void presignRequiresOnlyAccessWhileConfirmationAlsoRequiresCsrfAndRejectsMultipart() throws Exception {
        mvc.perform(get(PATH + "/presign").param("fileName", "avatar.png")
                        .param("contentType", "image/png").param("fileSize", "100"))
                .andExpect(status().isUnauthorized());
        mvc.perform(presignRequest("image/png", "100")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(post(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(post(PATH).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(PATH).file(new org.springframework.mock.web.MockMultipartFile("file", "x.png", "image/png", new byte[]{1}))
                        .cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void documentsPresignAsGetQueryParametersAndRejectsMissingOrMalformedValues() throws Exception {
        mvc.perform(get(PATH + "/presign").cookie(access)).andExpect(status().isBadRequest());
        mvc.perform(presignRequest("image/png", "not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(presignRequest("image/png", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/presign").cookie(access).param("fileName", "")
                        .param("contentType", "image/png").param("fileSize", "100"))
                .andExpect(status().isBadRequest());
        var docs = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var operation = docs.get("paths").get(PATH + "/presign");
        assertThat(operation.has("post")).isFalse();
        assertThat(operation.get("get").has("requestBody")).isFalse();
        assertThat(operation.get("get").get("parameters").size()).isEqualTo(3);
        for (JsonNode parameter : operation.get("get").get("parameters")) {
            assertThat(parameter.get("in").asString()).isEqualTo("query");
            assertThat(parameter.get("required").asBoolean()).isTrue();
        }
    }

    private JsonNode presign(String contentType, int size) throws Exception {
        return result(mvc.perform(presignRequest(contentType, Integer.toString(size))).andExpect(status().isOk()).andReturn());
    }

    private MockHttpServletRequestBuilder presignRequest(String contentType, String size) {
        return get(PATH + "/presign").cookie(access).param("fileName", "avatar.png")
                .param("contentType", contentType).param("fileSize", size);
    }

    private MockHttpServletRequestBuilder confirm(String key) throws Exception {
        return write(PATH, json.writeValueAsString(java.util.Map.of("key", key)));
    }

    private MockHttpServletRequestBuilder write(String path, String body) {
        return post(path).cookie(access, csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode result(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("result");
    }

    private void putS3(JsonNode presign, byte[] bytes) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(presign.get("uploadUrl").asString())).timeout(Duration.ofSeconds(10))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        presign.get("headers").properties().forEach(entry -> request.header(entry.getKey(), entry.getValue().asString()));
        try (var client = HttpClient.newHttpClient()) {
            assertThat(client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        }
    }

    private byte[] image(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output)).isTrue();
        return output.toByteArray();
    }
}
