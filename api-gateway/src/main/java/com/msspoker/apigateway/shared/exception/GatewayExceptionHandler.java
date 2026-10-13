package com.msspoker.apigateway.shared.exception;

import io.netty.handler.timeout.ReadTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final GatewayErrorWriter errors;

    public GatewayExceptionHandler(GatewayErrorWriter errors) { this.errors = errors; }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        LOG.warn("Gateway request {} failed: {} {}", exchange.getResponse().getHeaders().getFirst("X-Request-Id"), exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(), exception);
        if (exception instanceof ResponseStatusException status) {
            if (status.getStatusCode().value() == 504) return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_TIMEOUT);
            if (status.getStatusCode().value() == 503) return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_UNAVAILABLE);
            if (status.getStatusCode().value() == 404) return errors.write(exchange, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND);
            return errors.write(exchange, GatewayErrorCode.GATEWAY_REQUEST_FAILED, status.getStatusCode());
        }
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException) {
            return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_TIMEOUT);
        }
        if (cause instanceof IOException) {
            return errors.write(exchange, GatewayErrorCode.GATEWAY_UPSTREAM_UNAVAILABLE);
        }
        return errors.write(exchange, GatewayErrorCode.GATEWAY_REQUEST_FAILED);
    }
}
