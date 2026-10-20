package com.msspoker.apigateway.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.util.Assert;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

@ConfigurationProperties("gateway.catalog")
public record GatewayCatalogProperties(Map<String, Service> services) {
    public GatewayCatalogProperties {
        Assert.notEmpty(services, "gateway.catalog.services must not be empty");
        services = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(services));
        var routeIds = new HashSet<String>();
        services.forEach((serviceId, service) -> {
            Assert.hasText(serviceId, "Service ID is required");
            Assert.notNull(service.uri(), "Service URI is required: " + serviceId);
            Assert.isTrue(List.of("http", "https", "lb").contains(service.uri().getScheme()),
                    "Service URI must use http, https or lb: " + serviceId);
            Assert.hasText(service.uri().getHost(), "Service URI must have a host: " + serviceId);
            Assert.notEmpty(service.routes(), "At least one route is required: " + serviceId);
            service.routes().forEach(route -> {
                Assert.hasText(route.id(), "Route ID is required");
                Assert.isTrue(routeIds.add(route.id()), "Duplicate route ID: " + route.id());
            });
            service.publicEndpoints().forEach(rule -> validateScope(service, rule.method(), rule.paths()));
            service.adminEndpoints().forEach(rule -> validateScope(service, rule.method(), rule.paths()));
            service.csrfExemptEndpoints().forEach(rule -> validateScope(service, rule.method(), rule.paths()));
            validateScope(service, null, service.csrfDelegatedPaths());
        });
    }

    private static void validateScope(Service service, HttpMethod method, List<String> paths) {
        for (String path : paths) {
            Assert.isTrue(service.routes().stream().anyMatch(route ->
                    (method == null || route.methods().isEmpty() || route.methods().contains(method))
                            && route.paths().stream().anyMatch(pattern -> PathPatternParser.defaultInstance
                                    .parse(pattern).matches(PathContainer.parsePath(path)))),
                    "Endpoint policy must belong to one of its service routes: " + path);
        }
    }

    private static List<String> paths(List<String> values) {
        Assert.notEmpty(values, "Endpoint paths must not be empty");
        for (String path : values) {
            Assert.isTrue(path != null && path.startsWith("/"), "Paths must start with /");
            PathPatternParser.defaultInstance.parse(path);
        }
        return List.copyOf(values);
    }

    public record Service(URI uri, List<Route> routes, List<PublicEndpoint> publicEndpoints,
            List<Endpoint> adminEndpoints, List<Endpoint> csrfExemptEndpoints, List<String> csrfDelegatedPaths) {
        public Service {
            routes = routes == null ? List.of() : List.copyOf(routes);
            publicEndpoints = publicEndpoints == null ? List.of() : List.copyOf(publicEndpoints);
            adminEndpoints = adminEndpoints == null ? List.of() : List.copyOf(adminEndpoints);
            csrfExemptEndpoints = csrfExemptEndpoints == null ? List.of() : List.copyOf(csrfExemptEndpoints);
            csrfDelegatedPaths = csrfDelegatedPaths == null ? List.of() : paths(csrfDelegatedPaths);
        }
    }

    public record Route(String id, List<String> paths, List<HttpMethod> methods, @DefaultValue("0") int stripPrefix) {
        public Route {
            paths = GatewayCatalogProperties.paths(paths);
            methods = methods == null ? List.of() : List.copyOf(methods);
            Assert.isTrue(stripPrefix >= 0, "strip-prefix must not be negative");
        }
    }

    public record Endpoint(HttpMethod method, List<String> paths) {
        public Endpoint {
            Assert.notNull(method, "Endpoint method is required");
            paths = GatewayCatalogProperties.paths(paths);
        }
    }

    public record PublicEndpoint(HttpMethod method, List<String> paths, @DefaultValue("IGNORE") Identity identity) {
        public PublicEndpoint {
            Assert.notNull(method, "Public endpoint method is required");
            paths = GatewayCatalogProperties.paths(paths);
            identity = identity == null ? Identity.IGNORE : identity;
        }
    }

    public enum Identity { IGNORE, OPTIONAL }
}
