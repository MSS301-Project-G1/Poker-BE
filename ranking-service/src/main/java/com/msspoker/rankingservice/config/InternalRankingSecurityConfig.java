package com.msspoker.rankingservice.config;

import com.msspoker.rankingservice.security.InternalRankingFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class InternalRankingSecurityConfig {
    @Bean
    public FilterRegistrationBean<InternalRankingFilter> internalRankingFilter(@Value("${ranking.internal-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new InternalRankingFilter(key));
        registration.addUrlPatterns("/internal", "/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
