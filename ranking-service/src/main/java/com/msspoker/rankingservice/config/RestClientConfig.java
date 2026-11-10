package com.msspoker.rankingservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Calls to other services go through Spring Cloud LoadBalancer: a client's base URL names the service
 * (http://wallet-service) and Eureka resolves where it runs. Without Eureka (dev profile) the names
 * resolve from spring.cloud.discovery.client.simple.instances.
 *
 * Not a default candidate: only @LoadBalanced injection points get it. The Eureka client builds its own
 * transport from an unqualified RestClient.Builder and must reach discovery-service by address.
 */
@Configuration
public class RestClientConfig {
    @Bean(defaultCandidate = false)
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
