package com.msspoker.competitionservice.config;

import com.msspoker.competitionservice.security.CompetitionGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class CompetitionGatewaySecurityConfig {
    @Bean
    public FilterRegistrationBean<CompetitionGatewayFilter> competitionGatewayFilter(@Value("${competition.gateway-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new CompetitionGatewayFilter(key));
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
