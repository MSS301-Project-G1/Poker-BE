package com.msspoker.matchmakingservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.msspoker.matchmakingservice.constant.MatchmakingConstants;
import com.msspoker.matchmakingservice.service.store.PresenceStore;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

class PresenceInterceptorTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private final PresenceStore presence = mock(PresenceStore.class);
    private final PresenceInterceptor interceptor =
            new PresenceInterceptor(presence, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void everyPlayerRequestMarksThePlayerAsPresent() {
        UUID accountId = UUID.randomUUID();
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/matchmaking/normal/status");
        request.addHeader(MatchmakingConstants.USER_ID_HEADER, accountId.toString());

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isTrue();
        verify(presence).touch(accountId, NOW);
    }

    @Test
    void requestsWithoutAValidPlayerAreLeftToTheController() {
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/api/party/me");
        MockHttpServletRequest malformed = new MockHttpServletRequest("GET", "/api/party/me");
        malformed.addHeader(MatchmakingConstants.USER_ID_HEADER, "not-a-uuid");

        assertThat(interceptor.preHandle(missing, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(malformed, new MockHttpServletResponse(), new Object()))
                .isTrue();
        verifyNoInteractions(presence);
    }
}
