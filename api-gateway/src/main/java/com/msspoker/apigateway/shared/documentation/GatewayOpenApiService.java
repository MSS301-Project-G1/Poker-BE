package com.msspoker.apigateway.shared.documentation;

import com.msspoker.apigateway.shared.config.GatewayCatalogProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "gateway.swagger.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayOpenApiService {
    private static final List<String> METHODS = List.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final List<String> GATEWAY_HEADERS = List.of("authorization", "x-user-id", "x-user-role", "x-dev-user",
            "x-tab-id", "x-xsrf-token", "x-game-service-key", "x-session-internal-key", "x-wallet-internal-key",
            "x-wallet-gateway-key", "x-shop-internal-key", "x-shop-gateway-key", "x-matchmaking-gateway-key",
            "x-ranking-gateway-key", "x-ranking-internal-key", "x-competition-gateway-key", "x-social-gateway-key",
            "x-chat-gateway-key", "x-social-internal-key", "x-chat-internal-key");
    private final GatewayCatalogProperties catalog;
    private final WebClient direct = GatewaySwaggerConfig.clientBuilder().build();
    private final WebClient balanced;
    private final String gameServiceKey;

    public GatewayOpenApiService(GatewayCatalogProperties catalog,
            @Qualifier("swaggerLoadBalancedWebClientBuilder") WebClient.Builder balanced,
            @Value("${gateway.game-service-key:}") String gameServiceKey) {
        this.catalog = catalog;
        this.balanced = balanced.build();
        this.gameServiceKey = gameServiceKey;
    }

    public List<Map<String, String>> definitions() {
        return catalog.services().entrySet().stream().filter(entry -> entry.getValue().documentationEnabled())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.of("name", entry.getKey(), "url", "/v3/api-docs/" + entry.getKey())).toList();
    }

    public Mono<Map<String, Object>> document(String serviceId) {
        var service = catalog.services().get(serviceId);
        if (service == null || !service.documentationEnabled()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        boolean discovery = "lb".equals(service.uri().getScheme());
        String origin = discovery ? "http://" + service.uri().getHost() : service.uri().toString();
        return (discovery ? balanced : direct).get().uri(origin + "/v3/api-docs")
                .headers(headers -> {
                    // Game protects even its docs with the existing gateway service key.
                    if (serviceId.equals("game-service")) headers.set("X-Game-Service-Key", gameServiceKey);
                }).retrieve().bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .switchIfEmpty(Mono.error(new IllegalStateException("Empty API documentation")))
                .map(document -> gatewayDocument(document, service))
                .onErrorMap(error -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "API documentation unavailable: " + serviceId, error));
    }

    private Map<String, Object> gatewayDocument(Map<String, Object> document, GatewayCatalogProperties.Service service) {
        if (!document.containsKey("openapi") || !(document.get("paths") instanceof Map<?, ?>)) {
            throw new IllegalStateException("Invalid OpenAPI document");
        }
        document.put("servers", List.of(Map.of("url", "/", "description", "API Gateway (same origin)")));
        var paths = new LinkedHashMap<String, Object>();
        object(document.get("paths")).forEach((path, value) -> {
            if (!path.startsWith("/api/")) return;
            var item = new LinkedHashMap<>(object(value));
            item.remove("servers");
            removeGatewayHeaders(item);
            for (String method : METHODS) {
                if (!item.containsKey(method)) continue;
                if (!routed(service, path, method)) { item.remove(method); continue; }
                var operation = object(item.get(method));
                operation.remove("servers");
                removeGatewayHeaders(operation);
                boolean publicApi = service.publicEndpoints().stream().anyMatch(rule -> rule.method().name().equalsIgnoreCase(method)
                        && rule.paths().stream().anyMatch(pattern -> matches(pattern, path)));
                operation.put("security", publicApi ? List.of() : List.of(Map.of("cookieAuth", List.of())));
            }
            if (METHODS.stream().anyMatch(item::containsKey)) paths.put(path, item);
        });
        document.put("paths", paths);
        var components = object(document.get("components"));
        components.put("securitySchemes", Map.of("cookieAuth", Map.of("type", "apiKey", "in", "cookie", "name", "accessToken",
                "description", "Login using Auth on this gateway. Cookies, CSRF and tab headers are sent automatically.")));
        document.put("components", components);
        document.remove("security");
        return document;
    }

    private boolean routed(GatewayCatalogProperties.Service service, String path, String method) {
        return service.routes().stream().anyMatch(route ->
                (route.methods().isEmpty() || route.methods().stream().anyMatch(value -> value.name().equalsIgnoreCase(method)))
                && route.paths().stream().anyMatch(pattern -> matches(pattern, path)));
    }

    private boolean matches(String pattern, String path) {
        return PathPatternParser.defaultInstance.parse(pattern).matches(PathContainer.parsePath(path));
    }

    private void removeGatewayHeaders(Map<String, Object> value) {
        if (value.get("parameters") instanceof List<?> parameters) {
            value.put("parameters", parameters.stream().filter(parameter -> {
                var fields = object(parameter);
                return !"header".equals(fields.get("in"))
                        || !GATEWAY_HEADERS.contains(String.valueOf(fields.get("name")).toLowerCase(Locale.ROOT));
            }).toList());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
    }
}
