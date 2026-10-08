package com.msspoker.gameservice.security;

import com.msspoker.gameservice.api.GameExceptionHandler.ErrorResponse;
import com.msspoker.gameservice.exception.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TrustedIngressFilter extends OncePerRequestFilter {
    private final Environment environment;
    private final JsonMapper json;
    @Value("${game.service-key:}") private String serviceKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/actuator/health") || path.startsWith("/actuator/health/") || path.equals("/ws/game");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            boolean dev = environment.acceptsProfiles(Profiles.of(GameHeaders.DEV_PROFILE));
            String path = request.getServletPath();
            if (!dev) {
                String supplied = request.getHeader(GameHeaders.SERVICE_KEY);
                if (serviceKey.isBlank() || supplied == null || !MessageDigest.isEqual(
                        serviceKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                    throw GameExceptions.unauthorized();
                }
            }
            if (path.startsWith("/api/")) {
                String user = request.getHeader(dev ? GameHeaders.DEV_USER : GameHeaders.USER_ID);
                if (user == null) throw GameExceptions.unauthorized();
                try {
                    request.setAttribute(GameHeaders.USER_ID, UUID.fromString(user));
                } catch (IllegalArgumentException ex) {
                    throw GameExceptions.unauthorized();
                }
                // Dev tooling is local-only and may exercise Admin without an identity service.
                if (path.startsWith("/api/admin/") && !dev
                        && !GameHeaders.ADMIN.equals(request.getHeader(GameHeaders.USER_ROLE))) {
                    throw GameExceptions.forbidden();
                }
            }
            chain.doFilter(request, response);
        } catch (GameApiException ex) {
            response.setStatus(ex.getStatus().value());
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(json.writeValueAsString(new ErrorResponse(ex.getCode(), ex.getMessage(),
                    Instant.now(), request.getHeader("X-Request-Id"))));
        }
    }
}
