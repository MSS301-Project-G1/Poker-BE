package com.msspoker.rankingservice.config;

import com.msspoker.rankingservice.security.RankingGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class RankingGatewaySecurityConfig {
    @Bean
    public FilterRegistrationBean<RankingGatewayFilter> rankingGatewayFilter(@Value("${ranking.gateway-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new RankingGatewayFilter(key));
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
