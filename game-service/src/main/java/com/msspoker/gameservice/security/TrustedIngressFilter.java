package com.msspoker.gameservice.security;

import com.msspoker.gameservice.constant.GameHeaders;
import com.msspoker.gameservice.constant.GamePaths;
import com.msspoker.gameservice.constant.GameSocketConstants;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.exception.GameExceptions;
import com.msspoker.gameservice.mapper.ErrorMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TrustedIngressFilter extends OncePerRequestFilter {
    private final Environment environment;
    private final JsonMapper json;
    private final ErrorMapper errors;
    @Value("${game.service-key:}") private String serviceKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals(GamePaths.HEALTH) || path.startsWith(GamePaths.HEALTH + "/")
                || path.equals(GameSocketConstants.ENDPOINT);
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
            if (path.startsWith(GamePaths.PUBLIC_API)) {
                String user = request.getHeader(dev ? GameHeaders.DEV_USER : GameHeaders.USER_ID);
                if (user == null) throw GameExceptions.unauthorized();
                try {
                    request.setAttribute(GameHeaders.USER_ID, UUID.fromString(user));
                } catch (IllegalArgumentException ex) {
                    throw GameExceptions.unauthorized();
                }
                // Dev tooling is local-only and may exercise Admin without an identity service.
                if (path.startsWith(GamePaths.ADMIN_API) && !dev
                        && !GameHeaders.ADMIN.equals(request.getHeader(GameHeaders.USER_ROLE))) {
                    throw GameExceptions.forbidden();
                }
            }
            chain.doFilter(request, response);
        } catch (GameApiException ex) {
            response.setStatus(ex.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(json.writeValueAsString(errors.error(ex, request.getHeader(GameHeaders.REQUEST_ID))));
        }
    }
}
