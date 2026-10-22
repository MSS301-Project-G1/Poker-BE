package com.msspoker.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {
    @Autowired RouteDefinitionLocator routes;

    @Test
    void contextLoads() {
    }

    @Test
    void socialChatRoutesKeepApiPathsAndProvideWebsocketEndpoints() {
        var definitions = routes.getRouteDefinitions().collectList().block();
        for (String id : java.util.List.of("social-service", "chat-service")) {
            var route = definitions.stream().filter(value -> value.getId().equals(id)).findFirst().orElseThrow();
            assertThat(route.getFilters()).isEmpty();
        }
        for (String name : java.util.List.of("social", "chat")) {
            var route = definitions.stream().filter(value -> value.getId().equals(name + "-websocket"))
                    .findFirst().orElseThrow();
            assertThat(route.getUri().getScheme()).isIn("ws", "wss");
            assertThat(route.getPredicates().toString()).contains("/ws/" + name);
        }
    }

    @Test
    void matchmakingRoutePreservesControllerPathAndIncludesParty() {
        var route = routes.getRouteDefinitions()
                .filter(definition -> definition.getId().equals("matchmaking-service"))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getPredicates().toString())
                .contains("/api/matchmaking/**")
                .contains("/api/party/**");
        assertThat(route.getFilters()).isEmpty();
    }

}
