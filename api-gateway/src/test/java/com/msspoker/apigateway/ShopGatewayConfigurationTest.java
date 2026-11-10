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

class ShopGatewayConfigurationTest {
    @Test
    void missingShopGatewayKeyReturns503BeforeForwarding() {
        var filter = new TrustedIdentityFilter(new GatewayErrorWriter(JsonMapper.builder().build()),
                "game", "social", "chat", "wallet", "");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/shop/products"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async().id("shop-service").uri("http://shop.test")
                .predicate(ignored -> true).build());
        filter.filter(exchange, ignored -> { throw new AssertionError("Must not forward without shop key"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GATEWAY_UPSTREAM_UNAVAILABLE");
    }
}
