package com.msspoker.apigateway.shared.security;

import com.msspoker.apigateway.shared.config.GatewayCatalogProperties;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;

@Component
public class GatewayEndpointPolicy {
    private final ServerWebExchangeMatcher publicEndpoints;
    private final ServerWebExchangeMatcher ignoredAuthentication;
    private final ServerWebExchangeMatcher adminEndpoints;
    private final ServerWebExchangeMatcher csrfBypass;

    public GatewayEndpointPolicy(GatewayCatalogProperties catalog,
            @Value("${gateway.swagger.enabled:true}") boolean swaggerEnabled) {
        List<ServerWebExchangeMatcher> publicRules = new ArrayList<>();
        List<ServerWebExchangeMatcher> ignoredRules = new ArrayList<>();
        List<ServerWebExchangeMatcher> adminRules = new ArrayList<>();
        List<ServerWebExchangeMatcher> csrfRules = new ArrayList<>();
        if (swaggerEnabled) {
            var documentation = ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,
                    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**");
            publicRules.add(documentation);
            ignoredRules.add(documentation);
        }
        catalog.services().values().forEach(service -> {
            service.publicEndpoints().forEach(rule -> {
                var matcher = ServerWebExchangeMatchers.pathMatchers(rule.method(), rule.paths().toArray(String[]::new));
                publicRules.add(matcher);
                if (rule.identity() == GatewayCatalogProperties.Identity.IGNORE) ignoredRules.add(matcher);
            });
            service.adminEndpoints().forEach(rule -> adminRules.add(ServerWebExchangeMatchers
                    .pathMatchers(rule.method(), rule.paths().toArray(String[]::new))));
            service.csrfExemptEndpoints().forEach(rule -> csrfRules.add(ServerWebExchangeMatchers
                    .pathMatchers(rule.method(), rule.paths().toArray(String[]::new))));
            if (!service.csrfDelegatedPaths().isEmpty()) {
                csrfRules.add(ServerWebExchangeMatchers.pathMatchers(service.csrfDelegatedPaths().toArray(String[]::new)));
            }
        });
        publicEndpoints = any(publicRules);
        ignoredAuthentication = any(ignoredRules);
        adminEndpoints = any(adminRules);
        csrfBypass = any(csrfRules);
    }

    private static ServerWebExchangeMatcher any(List<ServerWebExchangeMatcher> matchers) {
        return matchers.isEmpty() ? exchange -> ServerWebExchangeMatcher.MatchResult.notMatch()
                : ServerWebExchangeMatchers.matchers(matchers.toArray(ServerWebExchangeMatcher[]::new));
    }

    public ServerWebExchangeMatcher publicEndpoints() { return publicEndpoints; }
    public ServerWebExchangeMatcher ignoredAuthentication() { return ignoredAuthentication; }
    public ServerWebExchangeMatcher adminEndpoints() { return adminEndpoints; }
    public ServerWebExchangeMatcher csrfBypass() { return csrfBypass; }
}
