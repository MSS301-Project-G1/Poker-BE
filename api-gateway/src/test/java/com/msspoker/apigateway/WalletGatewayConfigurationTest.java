package com.msspoker.apigateway;

import com.msspoker.apigateway.shared.exception.GatewayErrorWriter;
import com.msspoker.apigateway.shared.security.TrustedIdentityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class WalletGatewayConfigurationTest {
    @Test
    void missingWalletGatewayKeyReturns503BeforeForwarding() {
        var filter = new TrustedIdentityFilter(new GatewayErrorWriter(tools.jackson.databind.json.JsonMapper.builder().build()), "game", "social", "chat", "");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/wallet/me"));
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async().id("wallet-service").uri("http://wallet.test")
                .predicate(ignored -> true).build());
        filter.filter(exchange, ignored -> { throw new AssertionError("Must not forward without wallet key"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("GATEWAY_UPSTREAM_UNAVAILABLE");
    }
}
