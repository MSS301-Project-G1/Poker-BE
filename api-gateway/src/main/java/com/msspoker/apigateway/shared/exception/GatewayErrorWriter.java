package com.msspoker.apigateway.shared.exception;

import com.msspoker.common.response.ErrorResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class GatewayErrorWriter {
    private final JsonMapper mapper;

    public GatewayErrorWriter(JsonMapper mapper) { this.mapper = mapper; }

    public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode error) {
        return write(exchange, error, error.getStatus());
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode error, HttpStatusCode status) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        byte[] body = mapper.writeValueAsBytes(ErrorResponse.builder().code(error.name())
                .message(error.getMessage()).timestamp(Instant.now())
                .requestId(response.getHeaders().getFirst("X-Request-Id")).build());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
