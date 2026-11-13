package com.msspoker.rankingservice.exception;

import com.msspoker.rankingservice.enums.RankingError;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RankingExceptionHandlerTest {
    @Autowired WebApplicationContext context;
    @Autowired RankingExceptionHandler handler;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void unknownRouteUsesStandardErrorFormat() throws Exception {
        mvc.perform(get("/api/ranking/unknown").header("X-Request-Id", "req-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RANK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.requestId").value("req-1"));
    }

    @Test
    void wrongMethodAndBodyTypeAreClientErrors() throws Exception {
        mvc.perform(get("/api/tutorial/complete").header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Request-Id", "req-405"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").value("req-405"));
        mvc.perform(post("/api/admin/seasons").header("X-User-Role", "ADMIN").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("RANK_INVALID_REQUEST"));
    }

    @Test
    void constraintViolationsOfConcurrentRequestsAreConflicts() {
        var response = handler.concurrentChange(new DataIntegrityViolationException("duplicate key"), new MockHttpServletRequest());
        assertEquals(409, response.getStatusCode().value());
        assertEquals(RankingError.RANK_CONFLICT, response.getBody().code());
    }
}
