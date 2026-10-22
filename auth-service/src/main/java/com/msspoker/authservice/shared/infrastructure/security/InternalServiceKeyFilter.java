package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.shared.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalServiceKeyFilter extends OncePerRequestFilter {
    private final String key;
    private final SecurityErrorHandler errors;

    public InternalServiceKeyFilter(String key, SecurityErrorHandler errors) {
        this.key = key;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String supplied = request.getHeader("X-Game-Service-Key");
        if (key.isBlank() || supplied == null || !MessageDigest.isEqual(
                key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            errors.write(request, response, ErrorCode.AUTH_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
