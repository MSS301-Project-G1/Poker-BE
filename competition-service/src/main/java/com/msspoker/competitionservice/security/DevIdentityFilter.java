package com.msspoker.competitionservice.security;

import com.msspoker.competitionservice.constant.CompetitionConstants;
import com.msspoker.competitionservice.constant.CompetitionHeaders;

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
 * Local development without the gateway, like game-service: the shared FE http client identifies the
 * player with X-Dev-User, which stands in for the gateway's X-User-Id. Such requests also act as ADMIN
 * when no role is sent, so the admin screens work without identity-service.
 */
@Component
@Profile("dev")
public class DevIdentityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String devUser = request.getHeader(CompetitionHeaders.DEV_USER);
        if (devUser == null || request.getHeader(CompetitionHeaders.USER_ID) != null) {
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
            if (CompetitionHeaders.USER_ID.equalsIgnoreCase(name)) return devUser;
            String value = super.getHeader(name);
            if (value == null && CompetitionHeaders.USER_ROLE.equalsIgnoreCase(name)) return CompetitionConstants.ADMIN_ROLE;
            return value;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (!CompetitionHeaders.USER_ID.equalsIgnoreCase(name) && !CompetitionHeaders.USER_ROLE.equalsIgnoreCase(name)) {
                return super.getHeaders(name);
            }
            String value = getHeader(name);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(Set.of(value));
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(CompetitionHeaders.USER_ID);
            names.add(CompetitionHeaders.USER_ROLE);
            return Collections.enumeration(names);
        }
    }
}
