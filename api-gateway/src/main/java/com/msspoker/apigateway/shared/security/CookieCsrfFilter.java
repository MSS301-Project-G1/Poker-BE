package com.msspoker.apigateway.shared.security;

import com.msspoker.apigateway.shared.exception.GatewayErrorCode;
import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

public class CookieCsrfFilter implements WebFilter {
    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
    private final GatewayErrorWriter errors;
    private final GatewayEndpointPolicy policy;

    public CookieCsrfFilter(GatewayErrorWriter errors, GatewayEndpointPolicy policy) {
        this.errors = errors;
        this.policy = policy;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        String path = request.getPath().value();
        if (SAFE_METHODS.contains(request.getMethod()) || !path.startsWith("/api/")) return chain.filter(exchange);
        return policy.csrfBypass().matches(exchange).flatMap(match ->
                match.isMatch() ? chain.filter(exchange) : validate(exchange, chain));
    }

    private Mono<Void> validate(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        var cookies = request.getCookies().get("XSRF-TOKEN");
        var headers = request.getHeaders().get("X-XSRF-TOKEN");
        if (cookies == null || cookies.size() != 1 || headers == null || headers.size() != 1
                || cookies.getFirst().getValue().isBlank() || headers.getFirst().length() > 256
                || !MessageDigest.isEqual(cookies.getFirst().getValue().getBytes(StandardCharsets.UTF_8),
                        headers.getFirst().getBytes(StandardCharsets.UTF_8))) {
            return errors.write(exchange, GatewayErrorCode.GATEWAY_CSRF_INVALID);
        }
        return chain.filter(exchange);
    }
}
