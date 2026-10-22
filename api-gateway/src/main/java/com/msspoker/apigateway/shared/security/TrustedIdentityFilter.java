package com.msspoker.apigateway.shared.security;

import com.msspoker.apigateway.shared.exception.GatewayErrorCode;
import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class TrustedIdentityFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter errors;
    private final String gameServiceKey;
    private final String socialGatewayKey;
    private final String chatGatewayKey;

    public TrustedIdentityFilter(GatewayErrorWriter errors, @Value("${gateway.game-service-key:}") String gameServiceKey,
            @Value("${gateway.social-gateway-key:}") String socialGatewayKey,
            @Value("${gateway.chat-gateway-key:}") String chatGatewayKey) {
        this.errors = errors;
        this.gameServiceKey = gameServiceKey;
        this.socialGatewayKey = socialGatewayKey;
        this.chatGatewayKey = chatGatewayKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.<Principal>getPrincipal().map(Optional::of).defaultIfEmpty(Optional.empty()).flatMap(principal -> {
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            boolean game = route != null && route.getId().equals("game-service");
            boolean social = route != null && route.getId().equals("social-service");
            boolean chat = route != null && route.getId().equals("chat-service");
            if (game && gameServiceKey.isBlank()) return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_UNAVAILABLE);
            if ((social && socialGatewayKey.isBlank()) || (chat && chatGatewayKey.isBlank())) {
                return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_UNAVAILABLE);
            }
            var request = exchange.getRequest().mutate().headers(headers -> {
                if (principal.orElse(null) instanceof JwtAuthenticationToken authentication) {
                    headers.set("X-User-Id", authentication.getToken().getSubject());
                    headers.set("X-User-Role", authentication.getToken().getClaimAsString("role"));
                }
                if (game) headers.set("X-Game-Service-Key", gameServiceKey);
                if (social) headers.set("X-Social-Gateway-Key", socialGatewayKey);
                if (chat) headers.set("X-Chat-Gateway-Key", chatGatewayKey);
            }).build();
            // CORS is owned by the gateway; upstream headers must not produce duplicate values.
            exchange.getResponse().beforeCommit(() -> {
                for (String header : List.of("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials")) {
                    var values = exchange.getResponse().getHeaders().get(header);
                    if (values != null && !values.isEmpty()) exchange.getResponse().getHeaders().set(header, values.getFirst());
                }
                return Mono.empty();
            });
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
