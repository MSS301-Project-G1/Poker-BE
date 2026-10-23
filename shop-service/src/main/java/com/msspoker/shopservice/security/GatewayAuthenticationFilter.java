package com.msspoker.shopservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filter chuyển tiếp danh tính đã được Gateway xác thực từ HttpOnly Cookie vào Spring Security Context.
 * Gateway tự động giải mã JWT, bóc Subject và Role, rồi inject vào 2 headers tin cậy:
 * - X-User-Id: UUID của người dùng
 * - X-User-Role: ADMIN hoặc USER
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(HEADER_USER_ID);
        String userRole = request.getHeader(HEADER_USER_ROLE);

        if (userId != null && !userId.isBlank()) {
            try {
                UUID accountId = UUID.fromString(userId.trim());
                List<GrantedAuthority> authorities = new ArrayList<>();

                if (userRole != null && !userRole.isBlank()) {
                    String roleName = userRole.trim().toUpperCase();
                    if (!roleName.startsWith("ROLE_")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                    } else {
                        authorities.add(new SimpleGrantedAuthority(roleName));
                    }
                } else {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }

                PreAuthenticatedAuthenticationToken authentication =
                        new PreAuthenticatedAuthenticationToken(accountId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID
            }
        }

        chain.doFilter(request, response);
    }
}
