package com.msspoker.authservice.shared.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = cookies.read(request, AuthCookieService.ACCESS_COOKIE);
        if (raw != null && !raw.isBlank()) {
            try {
                var jwt = jwtService.verify(raw);
                var authentication = UsernamePasswordAuthenticationToken.authenticated(jwt, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException exception) {
                // An expired access cookie must not prevent refresh/login/logout.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
