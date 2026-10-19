package com.msspoker.apigateway.shared.config;

import com.msspoker.apigateway.shared.exception.GatewayErrorCode;
import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import com.msspoker.apigateway.shared.security.CookieCsrfFilter;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class GatewaySecurityConfig {
    private static final String[] PUBLIC_GET = {"/api/auth/csrf", "/.well-known/jwks.json", "/actuator/health",
            "/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
            "/api/ranking/leaderboard", "/api/seasons/current", "/api/seasons/*/rewards",
            "/api/rooms", "/api/tournaments", "/api/tournaments/*"};
    private static final String[] PUBLIC_POST = {"/api/auth/register", "/api/auth/register/verify-otp",
            "/api/auth/register/resend-otp", "/api/auth/password/forgot", "/api/auth/password/reset", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/api/payments/payos/webhook"};

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(@Value("${gateway.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${gateway.jwt.issuer}") String issuer, @Value("${gateway.jwt.audience}") String audience) {
        var client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                                .responseTimeout(Duration.ofSeconds(5))))
                .build();
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256).webClient(client).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(issuer), new JwtClaimValidator<List<String>>("aud", value -> value != null && value.contains(audience)),
                new JwtClaimValidator<String>("sub", value -> {
                    try { return value != null && UUID.fromString(value).toString().equals(value); }
                    catch (IllegalArgumentException exception) { return false; }
                }), new JwtClaimValidator<String>("role", value -> "USER".equals(value) || "ADMIN".equals(value)),
                new JwtClaimValidator<Instant>("exp", value -> value != null),
                new JwtClaimValidator<Instant>("iat", value -> value != null && !value.isAfter(Instant.now())),
                new JwtClaimValidator<Instant>("nbf", value -> value != null),
                new JwtClaimValidator<String>("jti", value -> value != null && !value.isBlank())));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http, ReactiveJwtDecoder decoder, GatewayErrorWriter errors,
            @Value("${gateway.cors.allowed-origins}") List<String> allowedOrigins) {
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("Cookie CORS requires explicit origins");
        }
        var manager = new JwtReactiveAuthenticationManager(decoder);
        manager.setJwtAuthenticationConverter(jwt -> Mono.just(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))))));
        var authentication = new AuthenticationWebFilter(manager);
        authentication.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        authentication.setRequiresAuthenticationMatcher(new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.matchers(
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, PUBLIC_GET),
                ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST, PUBLIC_POST))));
        authentication.setServerAuthenticationConverter(exchange -> {
            var cookies = exchange.getRequest().getCookies().get("accessToken");
            if (cookies == null || cookies.size() != 1 || cookies.getFirst().getValue().isBlank()) return Mono.empty();
            return Mono.just(new BearerTokenAuthenticationToken(cookies.getFirst().getValue()));
        });
        authentication.setAuthenticationFailureHandler((exchange, exception) -> exchange.getChain().filter(exchange.getExchange()));

        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id"));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        return http.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(config -> config.requestCache(NoOpServerRequestCache.getInstance()))
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable()).logout(config -> config.disable())
                // Auth validates auth CSRF; CookieCsrfFilter validates other API writes using that same token.
                .csrf(config -> config.disable()).cors(config -> config.configurationSource(source))
                .authorizeExchange(config -> config
                        .pathMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .pathMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/shop/categories").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/api/shop/categories/*").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/api/shop/categories/*").hasRole("ADMIN")
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/**").authenticated().anyExchange().denyAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((exchange, exception) -> errors.write(exchange, GatewayErrorCode.GATEWAY_UNAUTHENTICATED))
                        .accessDeniedHandler((exchange, exception) -> errors.write(exchange, GatewayErrorCode.GATEWAY_FORBIDDEN)))
                .addFilterAt(authentication, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(new CookieCsrfFilter(errors), SecurityWebFiltersOrder.AUTHORIZATION).build();
    }
}
