package com.msspoker.authservice.controller;

import com.msspoker.authservice.shared.common.exception.ApiException;
import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.msspoker.authservice.shared.infrastructure.security.SessionGuard;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Hidden
@RestController
public class InternalSessionController {
    private final JwtService jwt;
    private final SessionGuard sessions;
    private final String internalKey;

    public InternalSessionController(JwtService jwt, SessionGuard sessions,
            @Value("${auth.session.internal-key}") String internalKey) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.internalKey = internalKey;
    }

    @GetMapping("/internal/auth/session/check")
    public ResponseEntity<Void> check(@RequestHeader(value = "Authorization", defaultValue = "") String authorization,
            @RequestHeader(value = "X-Tab-Id", defaultValue = "") String tabId,
            @RequestHeader(value = "X-Session-Internal-Key", defaultValue = "") String key) {
        if (internalKey.isBlank() || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8))) return ResponseEntity.status(403).build();
        try {
            if (!authorization.startsWith("Bearer ")) throw new ApiException(ErrorCode.AUTH_UNAUTHENTICATED);
            sessions.verify(jwt.verify(authorization.substring(7)), tabId);
            return ResponseEntity.noContent().build();
        } catch (ApiException exception) {
            return ResponseEntity.status(exception.getErrorCode().getStatus())
                    .header("X-Session-Error", exception.getErrorCode().name()).build();
        } catch (JwtException exception) {
            return ResponseEntity.status(401).header("X-Session-Error", "AUTH_UNAUTHENTICATED").build();
        }
    }
}
