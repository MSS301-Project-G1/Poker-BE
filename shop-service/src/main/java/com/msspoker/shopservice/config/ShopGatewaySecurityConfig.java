package com.msspoker.shopservice.config;

import com.msspoker.shopservice.security.ShopGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class ShopGatewaySecurityConfig {
    @Bean
    public FilterRegistrationBean<ShopGatewayFilter> shopGatewayFilter(@Value("${shop.gateway-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new ShopGatewayFilter(key));
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
