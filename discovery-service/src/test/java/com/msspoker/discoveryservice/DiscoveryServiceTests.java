package com.msspoker.discoveryservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = DiscoveryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.server.use-read-only-response-cache=false", "eureka.server.wait-time-in-ms-when-sync-empty=0"})
class DiscoveryServiceTests {
    @Value("${local.server.port}") int port;

    @Test
    void serviceRegistersAndClientCanDiscoverItsAdvertisedAddress() {
        try (var client = new SpringApplicationBuilder(ClientConfiguration.class).web(WebApplicationType.NONE)
                .properties("spring.config.name=discovery-test-client", "spring.application.name=fixture-service",
                        "server.port=18999", "eureka.instance.hostname=127.0.0.1", "eureka.instance.prefer-ip-address=false",
                        "eureka.client.service-url.defaultZone=http://127.0.0.1:" + port + "/eureka/",
                        "eureka.client.initial-instance-info-replication-interval-seconds=1",
                        "eureka.client.jersey.enabled=false",
                        "eureka.client.registry-fetch-interval-seconds=1", "eureka.client.disable-delta=true")
                .run()) {
            DiscoveryClient discovery = client.getBean(DiscoveryClient.class);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var instances = discovery.getInstances("fixture-service");
                assertThat(instances).hasSize(1);
                assertThat(instances.getFirst().getHost()).isEqualTo("127.0.0.1");
                assertThat(instances.getFirst().getPort()).isEqualTo(18999);
            });
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ClientConfiguration {
    }
}
