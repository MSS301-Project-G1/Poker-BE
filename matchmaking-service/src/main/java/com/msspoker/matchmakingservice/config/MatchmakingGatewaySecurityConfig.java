package com.msspoker.matchmakingservice.config;

import com.msspoker.matchmakingservice.security.MatchmakingGatewayFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class MatchmakingGatewaySecurityConfig {
    @Bean
    public FilterRegistrationBean<MatchmakingGatewayFilter> matchmakingGatewayFilter(
            @Value("${matchmaking.gateway-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new MatchmakingGatewayFilter(key));
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
