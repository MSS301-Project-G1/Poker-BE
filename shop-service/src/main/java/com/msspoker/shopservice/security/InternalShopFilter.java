package com.msspoker.shopservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class InternalShopFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Shop-Internal-Key";
    private final String internalKey;

    public InternalShopFilter(String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var supplied = request.getHeaders(HEADER);
        String key = supplied.hasMoreElements() ? supplied.nextElement() : null;
        if (internalKey.isBlank() || key == null || supplied.hasMoreElements()
                || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"SHOP_INTERNAL_FORBIDDEN\",\"message\":\"Untrusted internal caller\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
