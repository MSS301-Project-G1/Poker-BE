package com.msspoker.authservice.shared.infrastructure.config;

import com.msspoker.authservice.shared.common.exception.ErrorCode;
import com.msspoker.authservice.shared.common.exception.GlobalExceptionHandler;
import com.msspoker.authservice.shared.infrastructure.security.AllowedOriginFilter;
import com.msspoker.authservice.shared.infrastructure.security.AuthCookieService;
import com.msspoker.authservice.shared.infrastructure.security.CookieAuthenticationFilter;
import com.msspoker.authservice.shared.infrastructure.security.JwtService;
import com.msspoker.authservice.shared.infrastructure.security.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain authSecurity(HttpSecurity http, AuthCookieService cookies, JwtService jwtService,
            AuthCookieProperties cookieProperties, GlobalExceptionHandler exceptionHandler, JsonMapper mapper,
            @Value("${auth.cors.allowed-origins}") List<String> allowedOrigins) throws Exception {

        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("Cookie CORS requires explicit origins");
        }

        SecurityErrorHandler errors = new SecurityErrorHandler(exceptionHandler, mapper);

        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();

        csrf.setCookieCustomizer(builder -> builder.httpOnly(true).secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite()).path("/"));

        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Request-Id"));
        cors.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);

        // Each cookie JWT is verified per request; it must not trigger a new-login CSRF reset.
        http.sessionManagement(config -> config.disable())
                .securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .cors(config -> config.configurationSource(source))
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/auth/register", "/api/auth/register/verify-otp", "/api/auth/register/resend-otp"))

                .authorizeHttpRequests(config -> config
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/api/auth/csrf",
                                "/actuator/health", "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/register/verify-otp",
                                "/api/auth/register/resend-otp", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/ws-token", "/api/profiles/batch", "/api/profiles/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/profiles/me").authenticated()
                        .anyRequest().denyAll())

                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> errors.write(request, response, ErrorCode.AUTH_UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) -> errors.write(request, response,
                                exception instanceof CsrfException ? ErrorCode.AUTH_CSRF_INVALID : ErrorCode.AUTH_FORBIDDEN)))

                .addFilterBefore(new AllowedOriginFilter(allowedOrigins, errors), CsrfFilter.class)

                .addFilterBefore(new CookieAuthenticationFilter(cookies, jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
