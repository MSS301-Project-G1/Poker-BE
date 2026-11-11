package com.msspoker.apigateway.shared.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** One Swagger selector for the services that publish an OpenAPI document. */
@Configuration(proxyBeanMethods = false)
public class GatewayOpenApiConfig {
    // Discovery and Identity are infrastructure/scaffolding and have no OpenAPI document.
    static final List<String> SERVICES = List.of("auth-service", "game-service", "matchmaking-service",
            "social-service", "chat-service", "ranking-service", "competition-service", "shop-service", "wallet-service");

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    RouteLocator openApiRoutes(RouteLocatorBuilder builder, GatewayCatalogProperties catalog) {
        var routes = builder.routes();
        SERVICES.forEach(id -> {
            var service = catalog.services().get(id);
            if (service == null) return;
            routes.route("openapi-" + id, route -> route.order(-1)
                    .path("/v3/api-docs/" + id).and().method(HttpMethod.GET)
                    .filters(filters -> filters.setPath("/v3/api-docs")
                            .modifyResponseBody(Map.class, Map.class, (exchange, document) -> {
                                if (document == null) return Mono.empty();
                                if (!document.containsKey("openapi")) return Mono.just(document);
                                var result = new LinkedHashMap(document);
                                // Try it out must use the browser's gateway, including in Docker.
                                result.put("servers", List.of(Map.of("url", "/")));
                                return Mono.just(result);
                            }))
                    .uri(service.uri()));
        });
        return routes.build();
    }

    @RestController
    static class SwaggerConfigurationController {
        private final GatewayCatalogProperties catalog;

        SwaggerConfigurationController(GatewayCatalogProperties catalog) {
            this.catalog = catalog;
        }

        @GetMapping("/v3/api-docs/swagger-config")
        Map<String, Object> configuration() {
            var urls = SERVICES.stream().filter(catalog.services()::containsKey)
                    .map(id -> Map.of("name", id, "url", "/v3/api-docs/" + id)).toList();
            return Map.of("urls", urls, "urls.primaryName", "auth-service", "validatorUrl", "");
        }
    }
}
