package com.msspoker.authservice.shared.infrastructure.security;

import com.msspoker.authservice.shared.infrastructure.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "accessToken";
    public static final String REFRESH_COOKIE = "refreshToken";
    private final AuthCookieProperties properties;
    private final Clock clock;

    public String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    public void write(HttpServletResponse response, SessionTokens tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), "/",
                Duration.between(clock.instant(), tokens.response().accessExpiresAt())));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(), "/api/auth",
                Duration.between(clock.instant(), tokens.response().refreshExpiresAt())));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", "/api/auth", Duration.ZERO));
    }

    private String cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(properties.secure())
                .sameSite(properties.sameSite()).path(path).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build().toString();
    }
}
