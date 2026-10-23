package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.shared.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class AllowedOriginFilter extends OncePerRequestFilter {
    private final List<String> allowedOrigins;
    private final SecurityErrorHandler errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            String origin = request.getHeader("Origin");
            if ((origin != null && !allowedOrigins.contains(origin))
                    || (origin == null && "cross-site".equals(request.getHeader("Sec-Fetch-Site")))) {
                errors.write(request, response, ErrorCode.AUTH_ORIGIN_NOT_ALLOWED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
