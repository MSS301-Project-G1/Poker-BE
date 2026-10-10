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
        JsonNode paths = document.get("paths");
        assertThat(paths.size()).isEqualTo(3);
        assertThat(paths.has("/api/auth/register")).isTrue();
        assertThat(paths.has("/api/auth/register/verify-otp")).isTrue();
        assertThat(paths.has("/api/auth/register/resend-otp")).isTrue();
        JsonNode register = paths.get("/api/auth/register").get("post");
        assertThat(register.get("responses").has("201")).isTrue();
        assertThat(register.get("responses").has("400")).isTrue();
        assertThat(register.get("responses").has("409")).isTrue();
        assertThat(register.get("responses").has("503")).isTrue();
        JsonNode schemas = document.get("components").get("schemas");
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
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

}
