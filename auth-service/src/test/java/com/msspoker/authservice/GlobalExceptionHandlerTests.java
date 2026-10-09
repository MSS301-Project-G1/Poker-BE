package com.msspoker.authservice;

import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTests {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void invalidBodyReturnsStandardErrorAndPreservesRequestId() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "review-123").content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").value("review-123"));
    }

    @Test
    void malformedJsonReturns400AndGeneratesRequestId() throws Exception {
        mvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId", notNullValue()));
    }

    @Test
    void frameworkErrorsKeepTheirHttpStatusAndHeaders() throws Exception {
        mvc.perform(get("/test/validate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("AUTH_METHOD_NOT_ALLOWED"));
        mvc.perform(post("/test/validate").contentType(MediaType.TEXT_PLAIN).content("email"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("AUTH_UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTH_NOT_FOUND"));
    }

    @Test
    void businessAndDatabaseErrorsAreControlled() throws Exception {
        mvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_CONFLICT"));
        mvc.perform(get("/test/database"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_CONFLICT"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void unexpectedErrorDoesNotExposeExceptionMessage() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AUTH_INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    // These endpoints exist only in the test, not in the application's API.
    @RestController
    static class TestController {
        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody TestRequest request) { }

        @GetMapping("/test/business")
        void business() { throw new ApiException(ErrorCode.AUTH_CONFLICT); }

        @GetMapping("/test/database")
        void database() { throw new DataIntegrityViolationException("secret SQL value"); }

        @GetMapping("/test/unexpected")
        void unexpected() { throw new IllegalStateException("secret internal value"); }
    }

    record TestRequest(@NotBlank @Email String email) { }
}
