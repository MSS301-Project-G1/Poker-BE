package com.msspoker.chatservice.security;

import com.msspoker.chatservice.config.ChatProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class TrustedIngressFilter extends OncePerRequestFilter {
    private final String gatewayKey, internalKey;
    private final tools.jackson.databind.json.JsonMapper json;

    public TrustedIngressFilter(
            ChatProperties properties, tools.jackson.databind.json.JsonMapper json) {
        this.json = json;
        this.gatewayKey = properties.getGatewayKey();
        this.internalKey = properties.getInternalKey();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean internal = path.equals("/internal") || path.startsWith("/internal/");
        if (internal || path.startsWith("/api/")) {
            String expected = internal ? internalKey : gatewayKey;
            String actual = req.getHeader(internal ? "X-Chat-Internal-Key" : "X-Chat-Gateway-Key");
            if (expected.isBlank()
                    || actual == null
                    || !MessageDigest.isEqual(
                            expected.getBytes(StandardCharsets.UTF_8),
                            actual.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(403);
                res.setContentType("application/json");
                res.getWriter()
                        .write(
                                json.writeValueAsString(
                                        new com.msspoker.common.response.ErrorResponse(
                                                "CHAT_FORBIDDEN",
                                                "Untrusted ingress",
                                                java.time.Instant.now(),
                                                req.getHeader("X-Request-Id"))));
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
