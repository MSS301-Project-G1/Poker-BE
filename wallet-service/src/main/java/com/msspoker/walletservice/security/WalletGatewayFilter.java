package com.msspoker.walletservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class WalletGatewayFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Wallet-Gateway-Key";
    private final String gatewayKey;

    public WalletGatewayFilter(String gatewayKey) {
        this.gatewayKey = gatewayKey;
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
            response.getWriter().write("{\"code\":\"WALLET_FORBIDDEN\",\"message\":\"Untrusted gateway caller\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
