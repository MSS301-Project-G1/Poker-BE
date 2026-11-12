package com.msspoker.apigateway.shared.documentation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "gateway.swagger.enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySwaggerController {
    private final GatewayOpenApiService documentation;

    public GatewaySwaggerController(GatewayOpenApiService documentation) { this.documentation = documentation; }

    @GetMapping("/swagger-ui.html")
    public ResponseEntity<Void> swagger() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/swagger-ui/index.html")).build();
    }

    @GetMapping("/v3/api-docs/swagger-config")
    public Map<String, Object> configuration() {
        return Map.of("urls", documentation.definitions(), "urls.primaryName", "auth-service", "validatorUrl", "",
                "withCredentials", true, "persistAuthorization", false);
    }

    @GetMapping("/v3/api-docs")
    public Mono<Map<String, Object>> authDocumentation() { return documentation.document("auth-service"); }

    @GetMapping("/v3/api-docs/{serviceId}")
    public Mono<Map<String, Object>> documentation(@PathVariable String serviceId) { return documentation.document(serviceId); }
}
