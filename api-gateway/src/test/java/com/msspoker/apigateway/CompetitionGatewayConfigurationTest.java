package com.msspoker.apigateway;

import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import com.msspoker.apigateway.shared.security.TrustedIdentityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class CompetitionGatewayConfigurationTest {
    @Test
    void missingCompetitionGatewayKeyReturns503BeforeForwarding() {
        var filter = new TrustedIdentityFilter(new GatewayErrorWriter(JsonMapper.builder().build()),
                "game", "social", "chat", "wallet", "shop", "matchmaking", "ranking", "");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/rooms"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async().id("competition-service").uri("http://competition.test")
                .predicate(ignored -> true).build());
        filter.filter(exchange, ignored -> { throw new AssertionError("Must not forward without competition key"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GATEWAY_UPSTREAM_UNAVAILABLE");
    }
}
