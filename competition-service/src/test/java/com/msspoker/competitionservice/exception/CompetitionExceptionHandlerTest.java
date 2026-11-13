package com.msspoker.competitionservice.exception;

import com.msspoker.competitionservice.enums.CompetitionError;

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
class CompetitionExceptionHandlerTest {
    @Autowired WebApplicationContext context;
    @Autowired CompetitionExceptionHandler handler;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void wrongMethodAndBodyTypeAreClientErrors() throws Exception {
        mvc.perform(get("/api/rooms/ABCDEF/start").header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Request-Id", "req-405"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("COMP_INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").value("req-405"));
        mvc.perform(post("/api/rooms").header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMP_INVALID_REQUEST"));
    }

    @Test
    void constraintViolationsOfConcurrentRequestsAreConflicts() {
        var response = handler.concurrentChange(new DataIntegrityViolationException("duplicate key"), new MockHttpServletRequest());
        assertEquals(409, response.getStatusCode().value());
        assertEquals(CompetitionError.COMP_CONFLICT, response.getBody().code());
    }
}
