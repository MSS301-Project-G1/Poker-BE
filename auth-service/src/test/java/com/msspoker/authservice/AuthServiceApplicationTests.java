package com.msspoker.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AuthServiceApplicationTests {
    @Autowired private WebApplicationContext context;
    @Autowired private JsonMapper jsonMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiDocumentsRegistrationRequestsAndActualResponseSchemas() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode document = jsonMapper.readTree(body);
        assertThat(document.get("info").get("title").asString()).isEqualTo("Poker Auth Service API");
        assertThat(document.path("servers").get(0).path("url").asString()).isEqualTo("/");
        JsonNode paths = document.get("paths");
        assertThat(paths.has("/api/auth/login")).isTrue();
        assertThat(paths.has("/api/auth/refresh")).isTrue();
        assertThat(paths.has("/api/auth/logout")).isTrue();
        assertThat(paths.has("/api/auth/register")).isTrue();
        assertThat(paths.has("/api/auth/register/verify-otp")).isTrue();
        assertThat(paths.has("/api/auth/register/resend-otp")).isTrue();
        // Spring supplies these arguments; Swagger must not ask clients to construct them.
        for (String path : new String[]{"/api/auth/csrf", "/api/auth/me", "/api/auth/ws-token"}) {
            JsonNode operation = paths.get(path).get("get");
            assertThat(operation.path("parameters").size()).as(path + " parameters").isZero();
            assertThat(operation.has("requestBody")).as(path + " request body").isFalse();
        }
        JsonNode register = paths.get("/api/auth/register").get("post");
        assertThat(register.get("responses").has("201")).isTrue();
        assertThat(register.get("responses").has("400")).isTrue();
        assertThat(register.get("responses").has("409")).isTrue();
        assertThat(register.get("responses").has("503")).isTrue();
        JsonNode schemas = document.get("components").get("schemas");
        for (String path : new String[]{"/api/auth/register", "/api/auth/register/verify-otp", "/api/auth/register/resend-otp"}) {
            String status = path.equals("/api/auth/register") ? "201" : "200";
            JsonNode content = paths.get(path).get("post").get("responses").get(status).get("content");
            JsonNode responseSchema = content.iterator().next().get("schema");
            JsonNode envelope = schemas.get(responseSchema.get("$ref").asString().substring("#/components/schemas/".length()));
            JsonNode envelopeProperties = envelope.get("properties");
            assertThat(envelopeProperties.has("code")).isTrue();
            assertThat(envelopeProperties.has("message")).isTrue();
            assertThat(envelopeProperties.has("timestamp")).isTrue();
            assertThat(envelopeProperties.has("path")).isTrue();
            String resultType = path.endsWith("verify-otp") ? "EmailVerificationResponse" : "RegistrationResponse";
            assertThat(envelopeProperties.get("result").get("$ref").asString())
                    .isEqualTo("#/components/schemas/" + resultType);
        }
        JsonNode properties = schemas.get("RegisterRequest").get("properties");
        assertThat(properties.has("confirmPassword")).isTrue();
        assertThat(properties.get("password").get("format").asString()).isEqualTo("password");
        assertThat(properties.get("password").get("writeOnly").asBoolean()).isTrue();
        assertThat(properties.get("confirmPassword").get("example").asString()).isEqualTo("Poker123!");
        assertThat(schemas.get("VerifyRegistrationOtpRequest").get("properties").get("otp")
                .get("example").asString()).isEqualTo("012345");
        assertThat(schemas.get("ErrorResponse").get("properties").has("requestId")).isTrue();
    }

    @Test
    void swaggerUiServesItsPageAndConnectsToTheLocalOpenApiDocument() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui-bundle.js")));
        mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
        // Behind the gateway, API document URLs must stay on the browser's origin.
        mvc.perform(get("/v3/api-docs/swagger-config").with(request -> {
            request.setServerName("auth-service");
            request.setServerPort(8090);
            return request;
        })).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.configUrl").value("/v3/api-docs/swagger-config"));
    }

}
