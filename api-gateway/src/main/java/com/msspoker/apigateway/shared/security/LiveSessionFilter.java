package com.msspoker.apigateway.shared.security;

import com.msspoker.apigateway.shared.exception.GatewayErrorCode;
import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Component
public class LiveSessionFilter {
    private final WebClient client;
    private final String endpoint;
    private final String key;
    private final GatewayErrorWriter errors;

    public LiveSessionFilter(GatewayErrorWriter errors,
            @Value("${gateway.session.check-url}") String endpoint,
            @Value("${gateway.session.internal-key}") String key) {
        this.errors = errors;
        this.endpoint = endpoint;
        this.key = key;
        client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                        .responseTimeout(Duration.ofSeconds(3)))).build();
    }

    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal().ofType(JwtAuthenticationToken.class).map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty()).flatMap(authentication -> {
                    if (authentication.isEmpty()) return chain.filter(exchange);
                    return check(exchange, authentication.get()).flatMap(error -> error.isEmpty()
                            ? chain.filter(exchange) : errors.write(exchange, error.get()));
                });
    }

    private Mono<java.util.Optional<GatewayErrorCode>> check(ServerWebExchange exchange, JwtAuthenticationToken jwt) {
        // Never cache this result: replacement must take effect before the access JWT expires.
        return client.get().uri(endpoint).headers(headers -> {
            headers.setBearerAuth(jwt.getToken().getTokenValue());
            headers.set("X-Session-Internal-Key", key);
            headers.set("X-Tab-Id", exchange.getRequest().getHeaders().getFirst("X-Tab-Id") == null
                    ? "" : exchange.getRequest().getHeaders().getFirst("X-Tab-Id"));
        }).exchangeToMono(response -> {
            GatewayErrorCode error = null;
            if (response.statusCode().value() != 204) {
                String code = response.headers().header("X-Session-Error").stream().findFirst().orElse("");
                error = switch (code) {
                    case "AUTH_SESSION_REPLACED" -> GatewayErrorCode.AUTH_SESSION_REPLACED;
                    case "AUTH_TAB_REPLACED" -> GatewayErrorCode.AUTH_TAB_REPLACED;
                    case "AUTH_EMAIL_VERIFICATION_PENDING" -> GatewayErrorCode.AUTH_EMAIL_VERIFICATION_PENDING;
                    case "AUTH_ACCOUNT_UNAVAILABLE" -> GatewayErrorCode.AUTH_ACCOUNT_UNAVAILABLE;
                    case "AUTH_ACCOUNT_BANNED" -> GatewayErrorCode.AUTH_ACCOUNT_BANNED;
                    case "AUTH_UNAUTHENTICATED", "AUTH_REFRESH_INVALID" -> GatewayErrorCode.GATEWAY_UNAUTHENTICATED;
                    default -> GatewayErrorCode.AUTH_SESSION_UNAVAILABLE;
                };
            }
            var result = java.util.Optional.ofNullable(error);
            return response.releaseBody().thenReturn(result);
        }).onErrorReturn(java.util.Optional.of(GatewayErrorCode.AUTH_SESSION_UNAVAILABLE));
    }
}
