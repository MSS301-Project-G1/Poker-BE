package com.msspoker.shopservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ShopGatewayFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Shop-Gateway-Key";
    private final String gatewayKey;

    public ShopGatewayFilter(String gatewayKey) {
        this.gatewayKey = gatewayKey;
    }

    public static boolean isPayosWebhook(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && request.getRequestURI().equals(request.getContextPath() + "/api/payments/payos/webhook");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // PayOS authenticates its callback with a signature, not the gateway key.
        return isPayosWebhook(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var supplied = request.getHeaders(HEADER);
        String key = supplied.hasMoreElements() ? supplied.nextElement() : null;
        if (gatewayKey.isBlank() || key == null || supplied.hasMoreElements()
                || !MessageDigest.isEqual(gatewayKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"SHOP_FORBIDDEN\",\"message\":\"Untrusted gateway caller\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
