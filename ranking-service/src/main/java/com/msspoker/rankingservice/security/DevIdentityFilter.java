package com.msspoker.rankingservice.security;

import com.msspoker.rankingservice.constant.RankingConstants;
import com.msspoker.rankingservice.constant.RankingHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Local development without the gateway, like game-service, which reads only X-Dev-User in dev:
 * X-Dev-User (the ?devUser identity) wins over X-User-Id, because the shared FE http client also adds
 * a default X-User-Id in dev. Requests without X-User-Role act as ADMIN, so the admin screens work
 * without identity-service.
 */
@Component
@Profile("dev")
public class DevIdentityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String devUser = request.getHeader(RankingHeaders.DEV_USER);
        if (devUser == null && request.getHeader(RankingHeaders.USER_ROLE) != null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new DevIdentityRequest(request, devUser), response);
    }

    private static final class DevIdentityRequest extends HttpServletRequestWrapper {
        private final String devUser;

        private DevIdentityRequest(HttpServletRequest request, String devUser) {
            super(request);
            this.devUser = devUser;
        }

        @Override
        public String getHeader(String name) {
            if (devUser != null && RankingHeaders.USER_ID.equalsIgnoreCase(name)) return devUser;
            String value = super.getHeader(name);
            if (value == null && RankingHeaders.USER_ROLE.equalsIgnoreCase(name)) return RankingConstants.ADMIN_ROLE;
            return value;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (!RankingHeaders.USER_ID.equalsIgnoreCase(name) && !RankingHeaders.USER_ROLE.equalsIgnoreCase(name)) {
                return super.getHeaders(name);
            }
            String value = getHeader(name);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            if (devUser != null) names.add(RankingHeaders.USER_ID);
            names.add(RankingHeaders.USER_ROLE);
            return Collections.enumeration(names);
        }
    }
}
