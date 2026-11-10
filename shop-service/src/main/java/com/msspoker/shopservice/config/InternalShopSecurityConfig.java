package com.msspoker.shopservice.config;

import com.msspoker.shopservice.security.InternalShopFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class InternalShopSecurityConfig {
    @Bean
    public FilterRegistrationBean<InternalShopFilter> internalShopFilter(@Value("${shop.internal-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new InternalShopFilter(key));
        registration.addUrlPatterns("/internal", "/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
