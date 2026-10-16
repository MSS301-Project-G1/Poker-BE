package com.msspoker.competitionservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class DevIdentityFilterTest {
    final DevIdentityFilter filter = new DevIdentityFilter();

    @Test
    void devUserStandsInForTheGatewayHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Dev-User", "11111111-1111-7111-8111-111111111111");
        request.addHeader("Accept", "application/json");
        request.addHeader("Accept", "text/plain");
        HttpServletRequest passed = run(request);
        assertEquals("11111111-1111-7111-8111-111111111111", passed.getHeader("X-User-Id"));
        assertEquals("ADMIN", passed.getHeader("X-User-Role"));
        assertEquals(1, Collections.list(passed.getHeaders("x-user-id")).size());
        assertEquals(2, Collections.list(passed.getHeaders("Accept")).size());
        assertTrue(Collections.list(passed.getHeaderNames()).contains("X-User-Id"));
    }

    @Test
    void gatewayHeadersAndRolesAreKept() throws Exception {
        MockHttpServletRequest gateway = new MockHttpServletRequest("GET", "/api/x");
        gateway.addHeader("X-Dev-User", "11111111-1111-7111-8111-111111111111");
        gateway.addHeader("X-User-Id", "22222222-2222-7222-8222-222222222222");
        assertEquals("22222222-2222-7222-8222-222222222222", run(gateway).getHeader("X-User-Id"));
        assertNull(run(gateway).getHeader("X-User-Role"));

        MockHttpServletRequest player = new MockHttpServletRequest("GET", "/api/x");
        player.addHeader("X-Dev-User", "11111111-1111-7111-8111-111111111111");
        player.addHeader("X-User-Role", "USER");
        assertEquals("USER", run(player).getHeader("X-User-Role"));

        assertNull(run(new MockHttpServletRequest("GET", "/api/x")).getHeader("X-User-Id"));
    }

    private HttpServletRequest run(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }
}
