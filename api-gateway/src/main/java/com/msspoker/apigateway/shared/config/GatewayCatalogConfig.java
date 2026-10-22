package com.msspoker.apigateway.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCatalogProperties.class)
public class GatewayCatalogConfig {
    @Bean
    RouteDefinitionLocator catalogRoutes(GatewayCatalogProperties catalog,
            @Value("${gateway.social-ws-url:ws://localhost:8084}") java.net.URI socialWs,
            @Value("${gateway.chat-ws-url:ws://localhost:8085}") java.net.URI chatWs) {
        List<RouteDefinition> definitions = new ArrayList<>();
        catalog.services().forEach((serviceId, service) -> service.routes().forEach(route -> {
            var definition = new RouteDefinition();
            definition.setId(route.id());
            definition.setUri(service.uri());
            definition.setMetadata(Map.of("service-id", serviceId));
            List<PredicateDefinition> predicates = new ArrayList<>();
            predicates.add(new PredicateDefinition("Path=" + String.join(",", route.paths())));
            if (!route.methods().isEmpty()) {
                predicates.add(new PredicateDefinition("Method=" + route.methods().stream()
                        .map(Object::toString).collect(Collectors.joining(","))));
            }
            definition.setPredicates(predicates);
            if (route.stripPrefix() > 0) {
                definition.setFilters(List.of(new FilterDefinition("StripPrefix=" + route.stripPrefix())));
            }
            definitions.add(definition);
        }));
        definitions.add(websocket("social-websocket", "/ws/social", socialWs));
        definitions.add(websocket("chat-websocket", "/ws/chat", chatWs));
        return () -> Flux.fromIterable(definitions);
    }

    private RouteDefinition websocket(String id, String path, java.net.URI uri) {
        org.springframework.util.Assert.isTrue(List.of("ws", "wss").contains(uri.getScheme()) && uri.getHost() != null,
                "WebSocket URI must use ws/wss and include a host: " + id);
        var route = new RouteDefinition();
        route.setId(id);
        route.setUri(uri);
        route.setPredicates(List.of(new PredicateDefinition("Path=" + path)));
        return route;
    }
}
