package com.msspoker.apigateway.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCatalogProperties.class)
public class GatewayCatalogConfig {
    @Bean
    RouteDefinitionLocator catalogRoutes(GatewayCatalogProperties catalog) {
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
        return () -> Flux.fromIterable(definitions);
    }
}
