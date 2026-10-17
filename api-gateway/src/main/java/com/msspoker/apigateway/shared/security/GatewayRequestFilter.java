package com.msspoker.apigateway.shared.security;

import com.msspoker.apigateway.shared.exception.GatewayErrorCode;
import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayRequestFilter implements WebFilter {
    private final GatewayErrorWriter errors;
    private final List<String> allowedOrigins;

    public GatewayRequestFilter(GatewayErrorWriter errors,
            @Value("${gateway.cors.allowed-origins}") List<String> allowedOrigins) {
        this.errors = errors;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();
        var request = exchange.getRequest().mutate().headers(headers -> {
            // Identity and forwarding headers must be derived by this gateway, never by clients.
            List.of("X-User-Id", "X-User-Role", "X-Game-Service-Key", "X-Dev-User", "Authorization",
                    "Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto",
                    "X-Forwarded-Port", "X-Forwarded-Prefix").forEach(headers::remove);
            headers.set("X-Request-Id", requestId);
        }).build();
        exchange = exchange.mutate().request(request).build();
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);
        String path = request.getPath().value();
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return errors.write(exchange, GatewayErrorCode.GATEWAY_FORBIDDEN);
        }
        String origin = request.getHeaders().getOrigin();
        if ((origin != null && !allowedOrigins.contains(origin))
                || (origin == null && "cross-site".equals(request.getHeaders().getFirst("Sec-Fetch-Site")))) {
            return errors.write(exchange, GatewayErrorCode.GATEWAY_ORIGIN_NOT_ALLOWED);
        }
        return chain.filter(exchange);
    }
}
