package com.msspoker.competitionservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DevIdentityFilterTest {
    static final String DEV_USER = "11111111-1111-7111-8111-111111111111";
    static final String DEFAULT_USER = "00000000-0000-0000-0000-000000000001";
    final DevIdentityFilter filter = new DevIdentityFilter();

    @Test
    void devUserStandsInForTheGatewayHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Dev-User", DEV_USER);
        request.addHeader("Accept", "application/json");
        request.addHeader("Accept", "text/plain");
        HttpServletRequest passed = run(request);
        assertEquals(DEV_USER, passed.getHeader("X-User-Id"));
        assertEquals("ADMIN", passed.getHeader("X-User-Role"));
        assertEquals(1, Collections.list(passed.getHeaders("x-user-id")).size());
        assertEquals(2, Collections.list(passed.getHeaders("Accept")).size());
        assertTrue(Collections.list(passed.getHeaderNames()).contains("X-User-Id"));
    }

    @Test
    void devUserWinsOverTheDefaultIdentityOfTheSharedHttpClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Dev-User", DEV_USER);
        request.addHeader("X-User-Id", DEFAULT_USER);
        HttpServletRequest passed = run(request);
        assertEquals(DEV_USER, passed.getHeader("X-User-Id"));
        assertEquals(1, Collections.list(passed.getHeaders("X-User-Id")).size());
    }

    @Test
    void requestsWithoutDevUserKeepTheirIdentityAndActAsAdminUnlessARoleIsSent() throws Exception {
        MockHttpServletRequest defaultUser = new MockHttpServletRequest("GET", "/api/x");
        defaultUser.addHeader("X-User-Id", DEFAULT_USER);
        assertEquals(DEFAULT_USER, run(defaultUser).getHeader("X-User-Id"));
        assertEquals("ADMIN", run(defaultUser).getHeader("X-User-Role"));

        MockHttpServletRequest player = new MockHttpServletRequest("GET", "/api/x");
        player.addHeader("X-Dev-User", DEV_USER);
        player.addHeader("X-User-Role", "USER");
        assertEquals("USER", run(player).getHeader("X-User-Role"));

        MockHttpServletRequest anonymous = new MockHttpServletRequest("GET", "/api/x");
        assertNull(run(anonymous).getHeader("X-User-Id"));
        assertFalse(Collections.list(run(anonymous).getHeaderNames()).contains("X-User-Id"));
    }

    private HttpServletRequest run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }
}
