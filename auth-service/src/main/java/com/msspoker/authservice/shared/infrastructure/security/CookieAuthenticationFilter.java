package com.msspoker.authservice.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.msspoker.authservice.shared.common.exception.ApiException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class CookieAuthenticationFilter extends OncePerRequestFilter {
    private final AuthCookieService cookies;
    private final JwtService jwtService;
    private final SessionGuard sessions;
    private final SecurityErrorHandler errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = cookies.read(request, AuthCookieService.ACCESS_COOKIE);
        if (raw != null && !raw.isBlank()) {
            try {
                var jwt = jwtService.verify(raw);
                String path = request.getRequestURI().substring(request.getContextPath().length());
                boolean publicAuth = java.util.Set.of("/api/auth/csrf", "/api/auth/login", "/api/auth/refresh",
                        "/api/auth/logout", "/api/auth/session/claim", "/api/auth/register",
                        "/api/auth/register/verify-otp", "/api/auth/register/resend-otp",
                        "/api/auth/password/forgot", "/api/auth/password/reset").contains(path);
                if (path.startsWith("/api/") && !publicAuth) sessions.verify(jwt, request.getHeader("X-Tab-Id"));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(jwt, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException exception) {
                // An expired access cookie must not prevent refresh/login/logout.
                SecurityContextHolder.clearContext();
            } catch (ApiException exception) {
                SecurityContextHolder.clearContext();
                errors.write(request, response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
