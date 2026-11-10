package com.msspoker.walletservice.config;

import com.msspoker.walletservice.security.WalletGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
public class WalletGatewaySecurityConfig {
    @Bean
    public FilterRegistrationBean<WalletGatewayFilter> walletGatewayFilter(@Value("${wallet.gateway-key:}") String key) {
        var registration = new FilterRegistrationBean<>(new WalletGatewayFilter(key));
        registration.addUrlPatterns("/api", "/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
