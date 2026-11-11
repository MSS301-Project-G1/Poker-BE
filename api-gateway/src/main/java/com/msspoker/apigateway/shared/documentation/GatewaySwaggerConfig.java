package com.msspoker.apigateway.shared.documentation;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gateway.swagger.enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySwaggerConfig implements WebFluxConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/swagger-ui/",
                        "classpath:/META-INF/resources/webjars/swagger-ui/5.32.14/");
    }

    @Bean
    @LoadBalanced
    WebClient.Builder swaggerLoadBalancedWebClientBuilder() {
        return clientBuilder();
    }

    static WebClient.Builder clientBuilder() {
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(
                HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                        .responseTimeout(Duration.ofSeconds(5))))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }
}
