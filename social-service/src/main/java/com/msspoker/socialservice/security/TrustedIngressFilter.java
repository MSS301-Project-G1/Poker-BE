package com.msspoker.socialservice.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TrustedIngressFilter extends OncePerRequestFilter {
  private final String gatewayKey, internalKey;
  private final tools.jackson.databind.json.JsonMapper json;

  public TrustedIngressFilter(
      @Value("${social.gateway-key:}") String gatewayKey,
      @Value("${social.internal-key:}") String internalKey,
      tools.jackson.databind.json.JsonMapper json) {
    this.json = json;
    this.gatewayKey = gatewayKey;
    this.internalKey = internalKey;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    boolean internal = path.equals("/internal") || path.startsWith("/internal/");
    if (internal || path.startsWith("/api/")) {
      String expected = internal ? internalKey : gatewayKey;
      String actual = req.getHeader(internal ? "X-Social-Internal-Key" : "X-Social-Gateway-Key");
      if (expected.isBlank()
          || actual == null
          || !MessageDigest.isEqual(
              expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
        res.setStatus(403);
        res.setContentType("application/json");
        res.getWriter()
            .write(
                json.writeValueAsString(
                    new com.msspoker.common.response.ErrorResponse(
                        "SOCIAL_FORBIDDEN",
                        "Untrusted ingress",
                        java.time.Instant.now(),
                        req.getHeader("X-Request-Id"))));
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
